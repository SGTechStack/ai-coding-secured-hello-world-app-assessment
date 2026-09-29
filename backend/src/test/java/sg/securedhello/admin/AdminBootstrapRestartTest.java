package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.TestSecrets;

/**
 * The bootstrap administrator across real startups ({@code restart}, own database), ADR-047: validation refuses a bad
 * seed before the port opens, and the runner seeds one forced-change administrator on an empty database and never
 * again.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapRestartTest {

    private static final String OTHER_ADMIN = "second-seed";

    @TempDir
    Path database;

    private Boot boot(Consumer<ConfigurableApplicationContext> whileRunning, String... args) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database)),
                whileRunning, args);
    }

    private static JdbcTemplate jdbc(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class);
    }

    private static List<Map<String, Object>> admins(ConfigurableApplicationContext context) {
        return jdbc(context).queryForList("SELECT username, enabled FROM users WHERE role = 'ADMIN'");
    }

    @Test
    @Proves("T-ADM-033")
    void theFirstBootOnAnEmptyDatabaseSeedsOneForcedChangeAdministrator() {
        Boot boot = boot(context -> {
            List<Map<String, Object>> users = jdbc(context).queryForList("SELECT * FROM users");
            assertThat(users).singleElement().satisfies(user -> {
                assertThat(user).containsEntry("USERNAME", TestSecrets.ADMIN_USERNAME).containsEntry("ROLE", "ADMIN")
                        .containsEntry("ENABLED", true).containsEntry("FORCE_PASSWORD_CHANGE", true);
                assertThat(user.get("ACTIVATED_AT")).isNotNull();
                assertThat(user.get("CREDENTIAL_ISSUED_AT")).isNotNull();
                String hash = (String) user.get("PASSWORD_HASH");
                assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain(TestSecrets.ADMIN_PASSWORD);
                assertThat(context.getBean(PasswordEncoder.class).matches(TestSecrets.ADMIN_PASSWORD, hash)).isTrue();
            });
            assertThat(jdbc(context).queryForObject("SELECT COUNT(*) FROM totp_user_details", Integer.class)).isZero();
        }, "--app.security.password.bcrypt-strength=12");

        assertThat(boot.failure()).isNull();
    }

    @Test
    @Proves("T-ADM-024")
    void aSecondBootSeedsNothing() {
        assertThat(boot(context -> assertThat(admins(context)).hasSize(1)).failure()).isNull();

        Boot second = boot(context -> {
            assertThat(admins(context)).singleElement()
                    .satisfies(admin -> assertThat(admin).containsEntry("USERNAME", TestSecrets.ADMIN_USERNAME));
            assertThat(jdbc(context).queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
        }, "--app.admin.username=" + OTHER_ADMIN);

        assertThat(second.failure()).isNull();
    }

    /** ADR-047: an existing, disabled administrator is never seeded around; startup goes on without a seed. */
    @Test
    @Proves("T-ADM-025")
    void aDisabledAdministratorIsNotSeededAround() {
        boot(context -> jdbc(context).update("UPDATE users SET enabled = FALSE WHERE role = 'ADMIN'"));

        Boot second = boot(context -> assertThat(admins(context)).singleElement()
                .satisfies(admin -> assertThat(admin).containsEntry("USERNAME", TestSecrets.ADMIN_USERNAME)
                        .containsEntry("ENABLED", false)), "--app.admin.username=" + OTHER_ADMIN);

        assertThat(second.failure()).isNull();
    }

    @Test
    @Proves("T-ADM-026")
    void aTombstonedSeedUsernameStopsStartup() {
        boot(context -> {
            JdbcTemplate jdbc = jdbc(context);
            jdbc.update("DELETE FROM users");
            jdbc.update("INSERT INTO deleted_users (user_id, username, email_hmac, deleted_at, deleted_by_id)"
                            + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), OTHER_ADMIN, "0".repeat(64),
                    Timestamp.from(Instant.now()), UUID.randomUUID());
        });

        Boot second = boot(context -> { }, "--app.admin.username=" + OTHER_ADMIN);

        assertThat(second.failure()).isNotNull();
        assertThat(second.failureMessages()).contains("app.admin.username", "tombstone");
        // Nothing was seeded: a boot with an untouched username is the first to seed.
        assertThat(boot(context -> assertThat(admins(context)).singleElement()
                .satisfies(admin -> assertThat(admin).containsEntry("USERNAME", TestSecrets.ADMIN_USERNAME)))
                .failure()).isNull();
    }

    @Test
    void aUsernameHeldByAnotherAccountStopsStartup() {
        boot(context -> {
            JdbcTemplate jdbc = jdbc(context);
            jdbc.update("DELETE FROM users");
            jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at)"
                    + " VALUES (?, ?, ?, 'USER', TRUE, ?)", UUID.randomUUID(), OTHER_ADMIN, "held@example.test",
                    Timestamp.from(Instant.now()));
        });

        Boot second = boot(context -> { }, "--app.admin.username=" + OTHER_ADMIN);

        assertThat(second.failureMessages()).contains("app.admin.username", "existing");
    }

    @ParameterizedTest
    @ValueSource(strings = {"administrator", "admin", "root", "Canary-Admin", "a"})
    @Proves("T-ADM-022")
    void aReservedOrMalformedSeedUsernameStopsStartupBeforeThePortOpens(String username) {
        Boot boot = boot(context -> { }, "--app.admin.username=" + username);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.admin.username");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-pass", "orchard-canary-admin-lantern", "aaaaaaaaaaaaaaaaaaaa"})
    @Proves("T-ADM-023")
    void aSeedPasswordThePolicyRefusesStopsStartupBeforeThePortOpens(String password, CapturedOutput output) {
        Boot boot = boot(context -> { }, "--app.admin.password=" + password);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.admin.password", "rule").doesNotContain(password);
        assertThat(output.getAll()).doesNotContain(password);
    }

    @ParameterizedTest
    @ValueSource(strings = {TestSecrets.ADMIN_USERNAME_PROPERTY, TestSecrets.ADMIN_PASSWORD_PROPERTY})
    @Proves("T-ADM-023")
    void aMissingSeedValueStopsStartupBeforeThePortOpens(String property) {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev")
                .initializers(RestartHarness.withoutTestSecret(property)));

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains(property);
    }
}
