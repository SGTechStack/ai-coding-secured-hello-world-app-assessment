package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxLockTimeoutTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * A wrong password whose failure count cannot take the account's row lock in time is still the uniform 401, never a
 * 500 (Std §3.2:247; Std §5:506), and it is still counted: deferred with its own time, then counted by the next outcome
 * that takes the lock (ADR-011 amendment of 2026-09-30). On {@code ctx-locktimeout}, whose 50 ms lock timeout ends the
 * wait quickly.
 */
class ContendedFailureCountedTest extends CtxLockTimeoutTest {

    private static final String SOURCE = "203.0.113.71";

    /** The lock threshold, as application.yml binds it (ADR-011). */
    private static final int THRESHOLD = 5;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private int failedLoginAttempts(Account account) {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
                account.id());
    }

    @Test
    @Proves("T-AUTH-016")
    void aContendedWrongPasswordIsTheUniform401AndIsStillCounted() throws Exception {
        Account account = new Accounts(jdbc, passwordEncoder).user();

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, account.id());
                assertThat(lock.executeQuery().next()).isTrue();
            }

            SignedIn.loginFrom(mockMvc, SOURCE, account.username(), Accounts.WRONG_PASSWORD)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

            holder.rollback();
        }
        assertThat(failedLoginAttempts(account)).as("deferred while the row was held").isZero();

        SignedIn.loginFrom(mockMvc, SOURCE, account.username(), Accounts.WRONG_PASSWORD)
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

        assertThat(failedLoginAttempts(account)).as("the contended failure and the next one").isEqualTo(2);
    }

    @Test
    @Proves("T-AUTH-016")
    void contendedFailuresThatReachTheThresholdRefuseTheNextCorrectPasswordAndKeepTheLock() throws Exception {
        Account account = new Accounts(jdbc, passwordEncoder).user();

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, account.id());
                assertThat(lock.executeQuery().next()).isTrue();
            }
            for (int i = 0; i < THRESHOLD; i++) {
                SignedIn.loginFrom(mockMvc, SOURCE, account.username(), Accounts.WRONG_PASSWORD)
                        .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            }
            holder.rollback();
        }

        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.loginFrom(mockMvc, SOURCE, account.username(), account.password())
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

            assertThat(audit.withMessage("Account locked.")).hasSize(1);
            assertThat(audit.withMessage("Account lock cleared.")).isEmpty();
            assertThat(audit.withMessage("Login succeeded.")).isEmpty();
            assertThat(audit.withMessage("Login failed.")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "ACCOUNT_LOCKED")
                    .containsEntry("user.id", account.id().toString()));
        }
        assertThat(failedLoginAttempts(account)).isEqualTo(THRESHOLD);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Timestamp.class,
                account.id()).toInstant()).isAfter(clock.instant());
    }
}
