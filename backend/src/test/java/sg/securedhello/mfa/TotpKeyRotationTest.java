package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.config.SecretsConfig;
import sg.securedhello.testsupport.TestSecrets;

/**
 * Yearly rotation through the real beans (ADR-022; R-CFG-005): after the operator moves the old key under
 * {@code retired-keys.<version>} and configures a new current key and version, a row sealed before the rotation still
 * opens, and new rows are sealed under the new version.
 */
class TotpKeyRotationTest {

    private static final String NEW_KEY = Base64.getEncoder().encodeToString(newKeyBytes());

    private final UUID owner = UUID.randomUUID();
    private final byte[] secret = new byte[TotpSecretCipher.SECRET_BYTES];

    private static byte[] newKeyBytes() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i * 29 + 131);
        }
        return key;
    }

    private static ApplicationContextRunner runner(Map<String, String> overrides) {
        Map<String, Object> properties = TestSecrets.properties();
        properties.putAll(overrides);
        List<String> pairs = new ArrayList<>();
        properties.forEach((name, value) -> pairs.add(name + "=" + value));
        pairs.add("app.mfa.totp.issuer=Issuer");
        return new ApplicationContextRunner()
                .withUserConfiguration(SecretsConfig.class, TotpConfig.class)
                .withBean(AuditEmitter.class, () -> mock(AuditEmitter.class))
                .withPropertyValues(pairs.toArray(String[]::new));
    }

    @Test
    void aRowSealedBeforeTheRotationStillOpensAndNewRowsUseTheNewVersion() {
        secret[3] = 42;
        AtomicReference<byte[]> sealedUnderVersionOne = new AtomicReference<>();
        runner(Map.of()).run(context -> {
            TotpSecretCipher cipher = context.getBean(TotpSecretCipher.class);
            assertThat(cipher.keyVersion()).isEqualTo(1);
            sealedUnderVersionOne.set(cipher.seal(owner, secret));
        });

        runner(Map.of(TestSecrets.TOTP_KEY_PROPERTY, NEW_KEY, TestSecrets.TOTP_KEY_VERSION_PROPERTY, "2",
                "app.mfa.totp.encryption.retired-keys.1", TestSecrets.TOTP_KEY)).run(context -> {
                    TotpSecretCipher cipher = context.getBean(TotpSecretCipher.class);

                    assertThat(cipher.keyVersion()).isEqualTo(2);
                    assertThat(cipher.open(owner, 1, sealedUnderVersionOne.get())).isEqualTo(secret);
                    assertThat(cipher.open(owner, 2, cipher.seal(owner, secret))).isEqualTo(secret);
                });
    }
}
