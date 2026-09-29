package sg.securedhello.mfa;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.config.KeyMaterial;

/**
 * The TOTP seed cipher: {@code AesGcmBytesEncryptor.withSecretKey(...)}, AES-256-GCM with a random 16-byte IV, under
 * the environment's validated TOTP keys (ADR-022). Never {@code AesBytesEncryptor}, {@code Encryptors} or a hand-built
 * {@code Cipher} (CVE-2026-47842).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TotpProperties.class)
class TotpConfig {

    /** The ring: the current key and every retired one still configured, each by its version (ADR-022). */
    @Bean
    TotpSecretCipher totpSecretCipher(ApplicationKeys keys, AuditEmitter audit) {
        Map<Integer, BytesEncryptor> ring = new HashMap<>();
        for (KeyMaterial key : keys.retiredTotpEncryption()) {
            ring.put(key.version(), aesGcm(key));
        }
        ring.put(keys.totpEncryption().version(), aesGcm(keys.totpEncryption()));
        return new TotpSecretCipher(ring, keys.totpEncryption().version(), audit);
    }

    private static BytesEncryptor aesGcm(KeyMaterial key) {
        return AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key.bytes(), "AES")).build();
    }
}
