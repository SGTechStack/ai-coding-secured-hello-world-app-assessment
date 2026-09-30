package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.audit.UnlockReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.security.ratelimit.AuthRateLimiter;
import sg.securedhello.security.ratelimit.RateLimit;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.DeviceCookies;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.DeviceLockState;
import sg.securedhello.user.PasswordLockoutState;

/**
 * {@code POST /api/admin/users/{uuid}/unlock} (R-AUTH-002; REJ-028; REJ-072): clears the password lockout and the
 * tier-1 factor lock of another account, never the NIST cap or tier 2, for a closed reason the audit row carries.
 */
class AdminUnlockTest extends CtxDefaultTest {

    private static final String UNLOCKED = "Account unlocked.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AuthRateLimiter limiter;

    private Accounts accounts;
    private TotpFactors factors;
    private Account actor;
    private AdminCredentialCalls admin;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
        actor = accounts.withRole("ADMIN");
        admin = AdminCredentialCalls.signedIn(mockMvc, factors, actor);
    }

    private Instant lockedUntil() {
        return clock.instant().plus(Duration.ofMinutes(20));
    }

    /** Locks the password (five windowed failures, a lock, five toward the cap) as the ladder would. */
    private void lockPassword(Account account) {
        jdbc.update("UPDATE users SET failed_login_attempts = 5, last_failed_at = ?, locked_until = ?,"
                + " consecutive_failures_since_success = 5 WHERE id = ?", Timestamp.from(clock.instant()),
                Timestamp.from(lockedUntil()), account.id());
    }

    /** Locks the factor's tier 1 (ten windowed failures and a lock), with ten failures toward tier 2. */
    private void lockFactor(Account account) {
        jdbc.update("UPDATE totp_user_details SET failed_attempts = 10, last_failed_at = ?, locked_until = ?,"
                + " cumulative_failures = 10 WHERE user_id = ?", Timestamp.from(clock.instant()),
                Timestamp.from(lockedUntil()), account.id());
    }

    private Map<String, Object> factorRow(Account account) {
        return jdbc.queryForMap("SELECT failed_attempts, last_failed_at, locked_until, cumulative_failures,"
                + " factor_disabled_at FROM totp_user_details WHERE user_id = ?", account.id());
    }

    private int login(Account account) throws Exception {
        return SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), account.password())
                .andReturn().getResponse().getStatus();
    }

    @Test
    @Proves("T-LCK-005")
    void unlockClearsThePasswordLockoutAndTheTierOneFactorLockAndTouchesNothingElse() throws Exception {
        Account target = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(target);
        lockPassword(target);
        lockFactor(target);
        assertThat(login(target)).as("locked").isEqualTo(401);
        // A per-source bucket, spent before the unlock: the unlock must leave it spent.
        String source = "unlock-" + UUID.randomUUID();
        long spent = 0;
        while (limiter.tryConsume(RateLimit.LOGIN_SOURCE, source).isEmpty()) {
            spent++;
        }
        assertThat(spent).isPositive();

        admin.unlock(target.id(), "USER_REQUEST").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.id().toString()));

        // Password lockout: the windowed counter, its anchor and the lock clear; the cap counter stays (ADR-013).
        assertThat(accounts.lockoutState(target)).isEqualTo(new PasswordLockoutState(0, null, null, 5, null));
        // Factor tier 1 clears; tier 2's cumulative count stays (ADR-027).
        assertThat(factorRow(target)).containsEntry("FAILED_ATTEMPTS", 0).containsEntry("LAST_FAILED_AT", null)
                .containsEntry("LOCKED_UNTIL", null).containsEntry("CUMULATIVE_FAILURES", 10)
                .containsEntry("FACTOR_DISABLED_AT", null);
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, source)).as("the source bucket").isPresent();
        // Well before the lock would have lifted, the correct password and the correct code both work again.
        factors.verified(mockMvc, SignedIn.as(mockMvc, target), secret);
    }

    /** REJ-072: tier 2 and the NIST cap are left for rebinding; an unlock does not lift either. */
    @Test
    @Proves("T-LCK-005")
    void unlockNeverClearsTheFactorsTierTwoDisableNorTheCapsPasswordDisable() throws Exception {
        Account target = accounts.withRole("ADMIN");
        factors.enrol(target);
        Timestamp disabledAt = Timestamp.from(clock.instant());
        jdbc.update("UPDATE users SET consecutive_failures_since_success = 100, password_disabled_at = ? WHERE id = ?",
                disabledAt, target.id());
        jdbc.update("UPDATE totp_user_details SET cumulative_failures = 100, factor_disabled_at = ?, failed_attempts = 3"
                + " WHERE user_id = ?", disabledAt, target.id());

        admin.unlock(target.id(), "FALSE_POSITIVE").andExpect(status().isOk());

        assertThat(accounts.lockoutState(target).passwordDisabled()).isTrue();
        assertThat(accounts.lockoutState(target).consecutiveFailuresSinceSuccess()).isEqualTo(100);
        assertThat(factorRow(target)).containsEntry("CUMULATIVE_FAILURES", 100).containsEntry("FAILED_ATTEMPTS", 0);
        assertThat(factorRow(target).get("FACTOR_DISABLED_AT")).isNotNull();
        assertThat(login(target)).as("the capped password is still refused").isEqualTo(401);
    }

    @ParameterizedTest
    @EnumSource(UnlockReason.class)
    void theUnlockRowNamesActorSubjectAndTheReason(UnlockReason reason) throws Exception {
        Account target = accounts.user();
        lockPassword(target);

        try (AuditCapture audit = AuditCapture.start()) {
            admin.unlock(target.id(), reason.name()).andExpect(status().isOk());
            assertThat(audit.withMessage(UNLOCKED)).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.action", "user-administration")
                            .containsEntry("user.id", actor.id().toString())
                            .containsEntry("user.target.id", target.id().toString())
                            .containsEntry("user.target.unlock_reason", reason.name()));
        }
        assertThat(login(target)).isEqualTo(200);
    }

    @Test
    void anAdministratorUnlockingThemselvesGetsAccessDeniedAndNothingChanges() throws Exception {
        lockPassword(actor);
        PasswordLockoutState locked = accounts.lockoutState(actor);

        try (AuditCapture audit = AuditCapture.start()) {
            admin.unlock(actor.id(), "OTHER").andExpect(problem(ErrorCode.ACCESS_DENIED));
            assertThat(audit.withMessage("Administrative action refused.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.reason", "SELF_ACTION"));
            assertThat(audit.withMessage(UNLOCKED)).isEmpty();
        }
        assertThat(accounts.lockoutState(actor)).isEqualTo(locked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"BECAUSE\"}", "{\"reason\":\"user_request\"}", "{\"reason\":null}"})
    void aMissingOrUnknownReasonIsInvalidAndNothingChanges(String body) throws Exception {
        Account target = accounts.user();
        lockPassword(target);
        PasswordLockoutState locked = accounts.lockoutState(target);

        admin.send("/api/admin/users/" + target.id() + "/unlock", body)
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));

        assertThat(accounts.lockoutState(target)).isEqualTo(locked);
    }

    @Test
    void anUnknownAccountIsDenied() throws Exception {
        admin.unlock(UUID.randomUUID(), "OTHER").andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves({"T-ADM-036", "T-LCK-005"})
    void theSignInStatusShowsEveryLockAndAnUnlockClearsTheTrustedDevicesLocksToo() throws Exception {
        Account target = accounts.withRole("ADMIN");
        factors.enrol(target);
        Cookie device = DeviceCookies.earn(mockMvc, "198.51.100.36", target);
        lockPassword(target);
        lockFactor(target);
        Instant until = accounts.lockoutState(target).lockedUntil();
        jdbc.update("UPDATE trusted_devices SET failed_login_attempts = 5, last_failed_at = ?, locked_until = ?,"
                + " consecutive_failures = 5 WHERE id = ?", Timestamp.from(clock.instant()), Timestamp.from(until),
                DeviceCookies.idOf(device));

        mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(admin.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.signInStatus.accountLockedUntil").value(until.toString()))
                .andExpect(jsonPath("$.signInStatus.failuresSinceSuccess").value(5))
                .andExpect(jsonPath("$.signInStatus.lockedDevices").value(1))
                .andExpect(jsonPath("$.signInStatus.nextDeviceUnlock").value(until.toString()))
                .andExpect(jsonPath("$.signInStatus.capDisabledAt").doesNotExist())
                .andExpect(jsonPath("$.signInStatus.factorLockedUntil").value(until.toString()))
                .andExpect(jsonPath("$.signInStatus.factorDisabled").value(false));
        String list = mockMvc.perform(get("/api/admin/users").cookie(admin.session().cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("\"lockedDevices\":1").doesNotContain(device.getValue())
                .doesNotContain(DeviceCookies.idOf(device).toString());

        admin.unlock(target.id(), "USER_REQUEST").andExpect(status().isOk())
                .andExpect(jsonPath("$.signInStatus.accountLockedUntil").doesNotExist())
                .andExpect(jsonPath("$.signInStatus.lockedDevices").value(0))
                .andExpect(jsonPath("$.signInStatus.factorLockedUntil").doesNotExist());
        assertThat(DeviceCookies.state(jdbc, device)).isEqualTo(DeviceLockState.CLEAR);
        assertThat(DeviceCookies.login(mockMvc, "198.51.100.36", device, target.username(), target.password())
                .getResponse().getStatus()).as("the device signs in again").isEqualTo(200);

        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                target.id());
        jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                Timestamp.from(clock.instant()), target.id());
        mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(admin.session().cookie()))
                .andExpect(jsonPath("$.signInStatus.capDisabledAt").exists())
                .andExpect(jsonPath("$.signInStatus.factorDisabled").value(true));
    }
}
