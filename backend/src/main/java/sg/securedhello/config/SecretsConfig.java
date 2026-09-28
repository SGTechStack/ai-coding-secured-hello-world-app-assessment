package sg.securedhello.config;

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

    private static final Logger log = LoggerFactory.getLogger(SecretsConfig.class);

    @Bean
    ApplicationKeys applicationKeys(TotpEncryptionProperties totp, HmacProperties hmac) {
        ApplicationKeys keys = new ApplicationKeys(
                KeyMaterial.decode(TOTP_KEY, totp.keyVersion(), totp.key()),
                KeyMaterial.decode(TOMBSTONE_KEY, hmac.tombstone().version(), hmac.tombstone().key()),
                KeyMaterial.decode(LOG_KEY, null, hmac.log().key()));
        for (KeyMaterial key : keys.all()) {
            log.info("Key loaded: property={} version={} fingerprint={}", key.property(),
                    key.version() == null ? "none" : key.version(), key.fingerprint());
        }
        return keys;
    }
}
