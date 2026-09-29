package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sg.securedhello.admin.AdminActions;
import sg.securedhello.mfa.TotpVerification.FactorDisabledException;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The tier-2 trip and {@code AdminActionGuard} take their row locks in the one order, {@code users} then
 * {@code totp_user_details} (ADR-048; TM-03), so contention between them is a wait and never a deadlock or a
 * {@code LOCK_TIMEOUT}. The guard's transaction is held open holding the {@code users} row, the first half of its lock
 * set, while the verification that trips tier 2 is seen waiting on it; then the guard takes the rest and commits, and
 * the verification must complete the trip. Were the verification to take the factor row first, it would hold what the
 * guard needs next while waiting on what the guard holds: a deadlock, which surfaces here as a lock timeout or a
 * deadlock error instead.
 *
 * <p>It shares {@code TwoAdminInvariantTest}'s context, whose 10 s lock timeout lets the guard hold its locks for as
 * long as it takes to see the other side waiting: under {@code ctx-locktimeout}'s 50 ms, the same proof would depend on
 * how fast the release follows.
 */
@TestPropertySource(properties = TemporaryH2FileInitializer.LOCK_TIMEOUT_PROPERTY + "=10000")
class FactorTripLockOrderTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AdminActions actions;

    @Autowired
    private TotpVerification verification;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Proves("T-MFA-007")
    void theTierTwoTripWaitsOnTheGuardsLockSetAndCompletesWithoutATimeoutOrADeadlock() throws Exception {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account actor = accounts.withRole("ADMIN");
        Account subject = accounts.withRole("ADMIN");
        factors.enrol(actor);
        byte[] secret = factors.enrol(subject);
        // One failure short of the tier-2 cap, with no tier-1 lock in force: the next wrong code trips it.
        jdbc.update("UPDATE totp_user_details SET cumulative_failures = ? WHERE user_id = ?",
                TotpUserDetails.DISABLE_THRESHOLD - 1, subject.id());
        String wrong = factors.code(secret).equals("000000") ? "000001" : "000000";
        CountDownLatch guardHoldsTheLocks = new CountDownLatch(1);
        CountDownLatch releaseGuard = new CountDownLatch(1);

        // A guarded mutation on the same admin (an enable, which changes nothing), paused after the first half of its
        // lock set, the users row, and before the factor row: the point where a factor-first verification deadlocks it.
        CompletableFuture<Void> guard = CompletableFuture.runAsync(() -> inRequest(() ->
                transactions.executeWithoutResult(status -> {
                    jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", subject.id());
                    guardHoldsTheLocks.countDown();
                    await(releaseGuard);
                    assertThat(actions.setEnabled(actor.id(), subject.id(), true)).isPresent();
                })));
        await(guardHoldsTheLocks);
        CompletableFuture<Void> trip = CompletableFuture.runAsync(() -> inRequest(() ->
                verification.verify(subject.id(), wrong)));

        while (blockedSessions() == 0) {
            if (trip.isDone()) {
                fail("the verification finished without waiting on the guard's lock set");
            }
            Thread.onSpinWait();
        }
        releaseGuard.countDown();
        guard.get();

        assertThatThrownBy(trip::get).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(FactorDisabledException.class);
        assertThat(jdbc.queryForObject("SELECT factor_disabled_at IS NOT NULL FROM totp_user_details WHERE user_id = ?",
                Boolean.class, subject.id())).as("the trip committed").isTrue();
    }

    /** H2 sessions waiting on another session's lock. */
    private int blockedSessions() {
        Integer blocked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Integer.class);
        return blocked == null ? 0 : blocked;
    }

    /** Runs {@code action} inside a request, which the audit rows are scoped to, as an {@code ADMIN}. */
    private static void inRequest(Runnable action) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/mfa/totp/verification")));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "contending-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).as("latch released").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
