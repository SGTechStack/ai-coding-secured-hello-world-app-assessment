package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/** ADR-048: the one authenticable predicate, read by the authenticable-admins gauge (R-OBS-007). */
class AuthenticableAdminsTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private AuthenticableAdmins admins;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    @Test
    @Proves("T-OBS-007")
    void theGaugeIsStronglyHeldAndReadsThePredicate() {
        long before = admins.count();
        enrolledAdmin();

        System.gc();

        double value = registry.get(AuthenticableAdmins.GAUGE).gauge().value();
        assertThat(value).isNotNaN().isEqualTo(before + 1.0);
    }

    @Test
    void anEnrolledAdminIsAuthenticableAndALockIsNotATerm() {
        long before = admins.count();
        UUID admin = enrolledAdmin();
        assertThat(admins.count()).isEqualTo(before + 1);

        Timestamp later = Timestamp.from(clock.instant().plusSeconds(3600));
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?", later, admin);
        jdbc.update("UPDATE totp_user_details SET locked_until = ? WHERE user_id = ?", later, admin);

        assertThat(admins.count()).as("a locked-out admin is still authenticable").isEqualTo(before + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE users SET enabled = FALSE WHERE id = ?",
            "UPDATE users SET activated_at = NULL WHERE id = ?",
            "UPDATE users SET password_disabled_at = CURRENT_TIMESTAMP WHERE id = ?",
            "UPDATE totp_user_details SET factor_disabled_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            "DELETE FROM totp_user_details WHERE user_id = ?",
            "UPDATE users SET role = 'USER' WHERE id = ?"})
    void eachTermRemovesTheAdmin(String change) {
        long before = admins.count();
        UUID admin = enrolledAdmin();

        jdbc.update(change, admin);

        assertThat(admins.count()).isEqualTo(before);
    }

    @Test
    void anAdminWithNoFactorIsNotAuthenticable() {
        long before = admins.count();
        accounts.withRole("ADMIN");

        assertThat(admins.count()).isEqualTo(before);
    }

    /** An active admin with a confirmed TOTP factor row (ADR-053). */
    private UUID enrolledAdmin() {
        UUID id = accounts.withRole("ADMIN").id();
        jdbc.update("INSERT INTO totp_user_details (user_id, totp_key, key_version, created_at) VALUES (?, ?, 1, ?)",
                id, new byte[69], Timestamp.from(Instant.EPOCH));
        return id;
    }
}
