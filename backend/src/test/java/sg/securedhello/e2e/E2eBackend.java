package sg.securedhello.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;

/**
 * The backend the Playwright suite signs in against (level E). It lives in the test sources, so nothing here can
 * reach a production build: the real application under the {@code dev} profile, on a fresh temporary H2 file, with the
 * test-only {@code TestSecrets}, plus the fixture accounts the browser tests sign in with and the {@link E2eMailbox} they
 * read activation links from. Started by {@code frontend/playwright.config.ts}:
 *
 * <pre>mvn -f backend/pom.xml test-compile spring-boot:test-run
 *     -Dspring-boot.run.main-class=sg.securedhello.e2e.E2eBackend</pre>
 *
 * Its defaults (port 18010, SPA origin {@code http://localhost:15110}) can be overridden with {@code --name=value}
 * arguments.
 */
public final class E2eBackend {

    /**
     * One fixture account per browser and test, so parallel tests never displace each other's session (one session per
     * account, the new login wins). Mirrored in {@code frontend/e2e/fixtures.ts}.
     */
    static final List<String> USERNAMES = List.of("chromium", "firefox").stream()
            .flatMap(browser -> List.of("hello", "service-worker", "change-password", "reset", "forced-change",
                    "golden-path", "disable-admin", "disable-user", "invite-admin").stream()
                    .map(test -> "e2e-" + browser + "-" + test))
            .toList();
    static final String PASSWORD = "e2e-password-correct-horse";

    /** The disable-admin administrators' TOTP secret, 20 bytes, mirrored in {@code e2e/admin-disable.spec.ts}. */
    static final byte[] ADMIN_TOTP_SECRET = "e2e-admin-disable-01".getBytes(StandardCharsets.US_ASCII);

    private static final String COMMAND_LINE = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;

    private E2eBackend() {
    }

    public static void main(String[] args) throws IOException {
        // Every browser test arrives from the one loopback source, so the suite's own traffic would spend the source
        // budgets (the CSRF bootstrap first) and refuse unrelated tests. They are raised as in the shared test contexts.
        Map<String, Object> defaults = new LinkedHashMap<>();
        PropertiesLoaderUtils.loadAllProperties("harness-budgets.properties")
                .forEach((name, value) -> defaults.put(String.valueOf(name), value));
        defaults.put("server.port", "18010");
        defaults.put("app.origins.spa", "http://localhost:15110");
        defaults.put("app.origins.api", "http://localhost:18010");
        new SpringApplicationBuilder(SecuredHelloApplication.class, Fixtures.class)
                .profiles("dev")
                .initializers(new TemporaryH2FileInitializer(), context -> {
                    // Above the configuration files, below the command line, which overrides any of them.
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    MapPropertySource e2e = new MapPropertySource("e2eDefaults", defaults);
                    if (sources.contains(COMMAND_LINE)) {
                        sources.addAfter(COMMAND_LINE, e2e);
                    } else {
                        sources.addFirst(e2e);
                    }
                })
                .run(args);
    }

    /**
     * The fixture accounts and the mailbox. Deliberately not a {@code @Configuration}: it sits inside the scanned
     * package, and the test contexts must never pick it up. {@link #main} passes it as a source instead, which Spring
     * processes as a lite configuration class.
     */
    static class Fixtures {

        @Bean
        ApplicationRunner e2eAccounts(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TotpSecretCipher cipher) {
            return arguments -> {
                USERNAMES.forEach(name -> new Accounts(jdbc, passwordEncoder).named(name, PASSWORD));
                // The forced-change accounts hold an issued credential, as the bootstrap seed does (ADR-046).
                jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = CURRENT_TIMESTAMP"
                        + " WHERE username LIKE 'e2e-%-forced-change'");
                // The golden-path accounts: administrators past the forced change, not yet enrolled.
                jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username LIKE 'e2e-%-golden-path'");
                // The disable-admin administrators: enrolled with a known secret, so the spec can answer the challenge.
                jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username LIKE 'e2e-%-disable-admin'");
                jdbc.queryForList("SELECT id FROM users WHERE username LIKE 'e2e-%-disable-admin'", UUID.class)
                        .forEach(id -> jdbc.update("INSERT INTO totp_user_details (user_id, totp_key, key_version,"
                                + " created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", id,
                                cipher.seal(id, ADMIN_TOTP_SECRET), cipher.keyVersion()));
                // The invite-admin administrators: enrolled with the same known secret, for e2e/admin-invite.spec.ts.
                jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username LIKE 'e2e-%-invite-admin'");
                jdbc.queryForList("SELECT id FROM users WHERE username LIKE 'e2e-%-invite-admin'", UUID.class)
                        .forEach(id -> jdbc.update("INSERT INTO totp_user_details (user_id, totp_key, key_version,"
                                + " created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", id,
                                cipher.seal(id, ADMIN_TOTP_SECRET), cipher.keyVersion()));
            };
        }

        /** Takes the dev link logger's place, so the browser tests read activation links from it (ADR-067). */
        @Bean
        @Primary
        E2eMailbox e2eMailbox() {
            return new E2eMailbox();
        }

        /** Serves the mailbox ahead of every application filter; it is not an application route. */
        @Bean
        FilterRegistrationBean<E2eMailbox> e2eMailboxEndpoint(E2eMailbox mailbox) {
            FilterRegistrationBean<E2eMailbox> registration = new FilterRegistrationBean<>(mailbox);
            registration.addUrlPatterns(E2eMailbox.PATH);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
