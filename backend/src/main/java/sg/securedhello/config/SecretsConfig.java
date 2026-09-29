package sg.securedhello.config;

import java.util.Comparator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds every secret through {@code @Validated @ConfigurationProperties} (ADR-062) and turns the three keys into
 * validated {@link KeyMaterial} during context refresh, before the web server opens its port. A missing secret fails
 * binding; a malformed or reused key fails here. Each key's fingerprint, never the key, is logged once.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TotpEncryptionProperties.class, HmacProperties.class, AdminSeedProperties.class,
        OriginsProperties.class})
public class SecretsConfig {

    static final String TOTP_KEY = "app.mfa.totp.encryption.key";
    static final String TOMBSTONE_KEY = "app.security.hmac.tombstone.key";
    static final String LOG_KEY = "app.security.hmac.log.key";
    static final String RETIRED_TOTP_KEYS = "app.mfa.totp.encryption.retired-keys";
    static final int MAX_KEY_VERSION = 255;

    private static final Logger log = LoggerFactory.getLogger(SecretsConfig.class);

    @Bean
    ApplicationKeys applicationKeys(TotpEncryptionProperties totp, HmacProperties hmac) {
        ApplicationKeys keys = new ApplicationKeys(
                KeyMaterial.decode(TOTP_KEY, totp.keyVersion(), totp.key()),
                KeyMaterial.decode(TOMBSTONE_KEY, hmac.tombstone().version(), hmac.tombstone().key()),
                KeyMaterial.decode(LOG_KEY, null, hmac.log().key()),
                totp.retiredKeys().entrySet().stream().map(SecretsConfig::retiredTotpKey)
                        .sorted(Comparator.comparing(KeyMaterial::version)).toList());
        for (KeyMaterial key : keys.all()) {
            log.info("Key loaded: property={} version={} fingerprint={}", key.property(),
                    key.version() == null ? "none" : key.version(), key.fingerprint());
        }
        return keys;
    }

    /**
     * Decodes one retired TOTP key after checking its version is 0..255 (ADR-022).
     *
     * @throws InvalidKeyMaterialException naming the property, never echoing the key
     */
    private static KeyMaterial retiredTotpKey(Map.Entry<String, String> retired) {
        String property = RETIRED_TOTP_KEYS + "." + retired.getKey();
        int version;
        try {
            version = Integer.parseInt(retired.getKey());
        } catch (NumberFormatException e) {
            version = -1;
        }
        if (version < 0 || version > MAX_KEY_VERSION) {
            throw new InvalidKeyMaterialException(property, "needs a key version from 0 to " + MAX_KEY_VERSION);
        }
        return KeyMaterial.decode(property, version, retired.getValue());
    }
}
