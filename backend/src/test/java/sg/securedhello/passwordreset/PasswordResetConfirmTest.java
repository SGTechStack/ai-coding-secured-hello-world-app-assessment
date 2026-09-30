package sg.securedhello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.user.PasswordLockoutState;

/**
 * {@code POST /api/password-reset/confirm} through the full context (PRD Story 7; ADR-007; ADR-009): a 30-minute,
 * single-use token sets the password through {@code PasswordService}, and clears the password lockout but never the
 * TOTP state.
 */
class PasswordResetConfirmTest extends CtxDefaultTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final String COMPLETED_ROW = "Password reset completed.";
    private static final String CLEARED_ROW = "Account lock cleared.";
    private static final String PENDING_TOTP =
            "SELECT RAWTOHEX(totp_key) AS totp_key, key_version, created_at FROM pending_totp WHERE user_id = ?";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CredentialTokens tokens;

    private Accounts accounts;
    private PasswordResets resets;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        resets = new PasswordResets(mockMvc);
    }

    /** Requests a reset for {@code account} and returns the token its email received. */
    private String requestedToken(Account account) throws Exception {
        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());
        return emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET).orElseThrow();
    }

    private int login(Account account, String password) throws Exception {
        return SignedIn.loginFrom(mockMvc, LOOPBACK, account.username(), password).andReturn().getResponse()
                .getStatus();
    }

    @Test
    void redeemingTheTokenReplacesThePassword() throws Exception {
        Account account = accounts.user();

        resets.confirm(requestedToken(account), PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(login(account, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
        assertThat(login(account, account.password())).as("the old password").isEqualTo(401);
    }

    @Test
    void theResetTokenWorksOnce() throws Exception {
        Account account = accounts.user();
        String token = requestedToken(account);
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

        resets.confirm(token, "another copper lantern drifts east").andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

        assertThat(login(account, PasswordResets.NEW_PASSWORD)).as("the first reset stands").isEqualTo(200);
    }

    @Test
    @Proves("T-CRED-013")
    void theResetTokenExpiresThirtyMinutesAfterIssueOnTheClock() throws Exception {
        Account inTime = accounts.user();
        Account late = accounts.user();
        String inTimeToken = requestedToken(inTime);
        String lateToken = requestedToken(late);

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        resets.confirm(inTimeToken, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

        clock.advance(Duration.ofSeconds(2));
        resets.confirm(lateToken, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        assertThat(login(late, late.password())).as("the password is unchanged").isEqualTo(200);
    }

    @Test
    void anUnknownOrMisshapenTokenIsInvalid() throws Exception {
        resets.confirm("D".repeat(43), PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        resets.confirm("not a token", PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
    }

    @Test
    @Proves("T-CRED-011")
    void aTokenOfOneTypeNeverRedeemsAsTheOtherAndIsNotConsumed() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        Registrations registrations = new Registrations(mockMvc);
        registrations.register(username, email).andExpect(status().isAccepted());
        String activation = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();
        Account account = accounts.user();
        String reset = requestedToken(account);

        resets.confirm(activation, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate(reset, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

        assertThat(unused(CredentialTokenType.ACTIVATION, activation)).isTrue();
        assertThat(unused(CredentialTokenType.PASSWORD_RESET, reset)).isTrue();
        registrations.activate(activation, Registrations.PASSWORD).andExpect(status().isNoContent());
        resets.confirm(reset, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
    }

    private boolean unused(CredentialTokenType type, String token) {
        return jdbc.queryForObject("SELECT used_at IS NULL FROM credential_tokens WHERE token_hash = ?", Boolean.class,
                CredentialTokenHash.hash(type, token));
    }

    @Test
    void anAdministratorIssuedTokenRedeemsHereToo() throws Exception {
        Account account = accounts.user();
        // As the admin reset endpoint will issue it (ADR-006): minted for the account, returned rather than mailed.
        String token = tokens.mint(account.id(), CredentialTokenType.PASSWORD_RESET);

        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(login(account, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void aRejectedPasswordDoesNotBurnTheTokenAndTheTokenIsCheckedFirst() throws Exception {
        Account account = accounts.user();
        String token = requestedToken(account);

        try (AuditCapture audit = AuditCapture.start()) {
            resets.confirm("E".repeat(43), "too short").andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
            assertThat(audit.rows()).as("no password-rejected row before a successful token check; only the refusal")
                    .singleElement().satisfies(row -> assertThat(row)
                            .containsEntry("message", "Credential token redemption failed.")
                            .containsEntry("event.reason", "TOKEN_UNKNOWN"));
        }
        resets.confirm(token, account.password()).andExpect(problem(ErrorCode.PASSWORD_REJECTED))
                .andExpect(jsonPath("$.rule").value("HISTORY_REUSE"));
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @Proves("T-LCK-017")
    void redemptionClearsThePasswordLockoutAndTheForcedChangeButNoTotpState() throws Exception {
        Account account = accounts.user();
        Timestamp failedAt = Timestamp.from(clock.instant());
        jdbc.update("UPDATE users SET failed_login_attempts = 5, last_failed_at = ?, locked_until = ?,"
                + " consecutive_failures_since_success = 42, force_password_change = TRUE, credential_issued_at = ?"
                + " WHERE id = ?", failedAt, Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), failedAt,
                account.id());
        jdbc.update("INSERT INTO totp_user_details (user_id, totp_key, key_version, last_used_counter, failed_attempts,"
                + " last_failed_at, locked_until, cumulative_failures, factor_disabled_at, created_at)"
                + " VALUES (?, ?, 1, 7, 3, ?, ?, 12, NULL, ?)", account.id(), new byte[69], failedAt,
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), failedAt);
        jdbc.update("INSERT INTO pending_totp (user_id, totp_key, key_version, created_at) VALUES (?, ?, 1, ?)",
                account.id(), new byte[69], failedAt);
        Map<String, Object> totpBefore = totp(account);
        Map<String, Object> pendingBefore = jdbc.queryForMap(PENDING_TOTP,
                account.id());
        String token = requestedToken(account);

        try (AuditCapture audit = AuditCapture.start()) {
            resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

            assertThat(audit.withMessage(COMPLETED_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.action", "password-reset").containsEntry("user.id", account.id().toString()));
            assertThat(audit.withMessage(CLEARED_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "PASSWORD_RESET_COMPLETED")
                    .containsEntry("user.id", account.id().toString()));
        }
        assertThat(accounts.lockoutState(account)).isEqualTo(PasswordLockoutState.CLEAR);
        assertThat(jdbc.queryForMap("SELECT force_password_change, credential_issued_at FROM users WHERE id = ?",
                account.id())).containsEntry("FORCE_PASSWORD_CHANGE", false).containsEntry("CREDENTIAL_ISSUED_AT", null);
        assertThat(totp(account)).as("TOTP counters, lock and enrolment").isEqualTo(totpBefore);
        assertThat(jdbc.queryForMap(PENDING_TOTP, account.id()))
                .as("pending enrolment").isEqualTo(pendingBefore);
        assertThat(login(account, PasswordResets.NEW_PASSWORD)).as("the locked account can sign in").isEqualTo(200);
    }

    /** The TOTP row, its key as hex so that the rows compare by value. */
    private Map<String, Object> totp(Account account) {
        return jdbc.queryForMap("SELECT RAWTOHEX(totp_key) AS totp_key, key_version, last_used_counter,"
                + " failed_attempts, last_failed_at, locked_until, cumulative_failures, factor_disabled_at, created_at"
                + " FROM totp_user_details WHERE user_id = ?",
                account.id());
    }

    @Test
    @Proves("T-CRED-024")
    void resetWorksWhileThePasswordIsDisabledAndRebindsIt() throws Exception {
        Account account = accounts.user();
        jdbc.update("UPDATE users SET consecutive_failures_since_success = 100, password_disabled_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), account.id());
        assertThat(login(account, account.password())).as("capped").isEqualTo(401);

        try (AuditCapture audit = AuditCapture.start()) {
            resets.confirm(requestedToken(account), PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

            assertThat(audit.withMessage(CLEARED_ROW)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "PASSWORD_RESET_COMPLETED"));
        }
        assertThat(accounts.lockoutState(account)).isEqualTo(PasswordLockoutState.CLEAR);
        assertThat(login(account, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void anAccountWithNothingToClearGetsNoLockClearedRow() throws Exception {
        Account account = accounts.user();
        String token = requestedToken(account);

        try (AuditCapture audit = AuditCapture.start()) {
            resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

            assertThat(audit.withMessage(COMPLETED_ROW)).singleElement();
            assertThat(audit.withMessage(CLEARED_ROW)).isEmpty();
        }
    }

    @Test
    @Proves("T-AUD-019")
    void neitherTheTokenNorItsHashReachesAnyOutputAcrossTheResetFlow() throws Exception {
        Account account = accounts.user();
        // Capturing the email registers the token and its stored hash with the output guard.
        String token = requestedToken(account);
        resets.confirm(token, "too short").andExpect(problem(ErrorCode.PASSWORD_REJECTED));
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

        assertThat(LogOutputGuard.scanNow(false)).as("stdout, stderr and the audit file").isEmpty();
    }
}
