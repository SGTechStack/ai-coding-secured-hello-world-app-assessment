package sg.securedhello.demo;

import jakarta.validation.constraints.NotBlank;

import org.apache.commons.codec.binary.Base32;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The dev-only demo accounts' committed values, from {@code application-dev.yml} ({@code app.dev.demo-accounts}). They
 * are public by design: bound under {@code dev} only, and refused outside it ({@code PublishedDemoValues}).
 *
 * @param enabled         whether startup seeds the demo accounts; the test harness turns it off
 * @param userPassword    the demo user's password
 * @param adminPassword   the demo administrator's password
 * @param adminTotpSecret the demo administrator's TOTP secret, RFC 4648 Base32
 */
@Validated
@ConfigurationProperties("app.dev.demo-accounts")
public record DemoAccountsProperties(boolean enabled, @NotBlank String userPassword, @NotBlank String adminPassword,
        @NotBlank String adminTotpSecret) {

    /** The administrator's TOTP secret as raw bytes. */
    public byte[] adminTotpSecretBytes() {
        return new Base32().decode(adminTotpSecret);
    }
}
