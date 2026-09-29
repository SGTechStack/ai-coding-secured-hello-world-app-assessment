package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sg.securedhello.audit.AdminRefusalReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The two-admin invariant (ADR-048; REJ-050), which counts every enrolled admin in the database. The shared contexts
 * accumulate enrolled admins from other tests, so it runs in {@code ctx-lockhold}, whose database only lock-holding
 * tests share, and whose 10 s lock timeout lets the race test hold a lock while the other side waits on it. Each test
 * starts from no enrolled admin: it deletes every factor row, which the other tests of that context do not rely on.
 */
class TwoAdminInvariantTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AdminActions actions;

    @Autowired
    private AuthenticableAdmins authenticable;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private TransactionTemplate transactions;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void startFromNoEnrolledAdmin() {
        jdbc.update("DELETE FROM totp_user_details");
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    /** An enrolled admin, signed in with a freshly verified factor. */
    private record Admin(Account account, CsrfSession session) {

        UUID id() {
            return account.id();
        }
    }

    private Admin enrolledAdmin() throws Exception {
        Account account = accounts.withRole("ADMIN");
        return new Admin(account, factors.verified(mockMvc, SignedIn.as(mockMvc, account), factors.enrol(account)));
    }

    private ResultActions disable(Admin actor, UUID subject) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + subject + "/enabled").with(actor.session().inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
    }

    private boolean enabled(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, id));
    }

    private long enrolledAdmins() {
        return transactions.execute(status -> authenticable.lockForChange(UUID.randomUUID()).stream()
                .filter(AuthenticableAdmins.Standing::enrolledAdmin).count());
    }

    @Test
    void withExactlyTwoEnrolledAdminsDisablingEitherIsRefusedWithTheNewCode() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();

        try (AuditCapture audit = AuditCapture.start()) {
            disable(a, b.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
            disable(b, a.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
            assertThat(audit.withMessage("Administrative action refused.")).hasSize(2).allSatisfy(row ->
                    assertThat(row).containsEntry("event.reason", "TWO_ADMIN_INVARIANT"));
        }
        assertThat(enabled(a.id())).isTrue();
        assertThat(enabled(b.id())).isTrue();
    }

    @Test
    void withThreeEnrolledAdminsDisablingOneSucceedsAndThenTheRemainingTwoAreProtected() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();
        Admin c = enrolledAdmin();

        disable(a, c.id()).andExpect(status().isOk());

        assertThat(enabled(c.id())).isFalse();
        disable(a, b.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
    }

    @Test
    void aPendingInviteIsNotCountedSoOneRealAdminPlusAnInviteDoNotMakeTwo() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();
        // An invited admin who has not redeemed: not activated, so not counted, even holding a factor row.
        Account invite = accounts.withRole("ADMIN");
        jdbc.update("UPDATE users SET activated_at = NULL WHERE id = ?", invite.id());
        factors.enrol(invite);

        disable(a, b.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
        disable(a, invite.id()).andExpect(status().isOk());
    }

    /** R-AUD-009: the applied row is written after commit, so a change that rolls back leaves no row claiming it. */
    @Test
    void aDisableThatRollsBackWritesNoAppliedRow() throws Exception {
        UUID actor = enrolledAdmin().id();
        Account target = accounts.user();

        try (AuditCapture audit = AuditCapture.start()) {
            asAdmin(() -> transactions.executeWithoutResult(status -> {
                assertThat(actions.setEnabled(actor, target.id(), false)).isPresent();
                status.setRollbackOnly();
            }));
            assertThat(audit.withMessage("Account disabled.")).isEmpty();
        }
        assertThat(enabled(target.id())).isTrue();
    }

    @Test
    void theGaugeAndTheGuardReadTheOneDefinition() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();
        Admin c = enrolledAdmin();
        assertThat(registry.get(AuthenticableAdmins.GAUGE).gauge().value()).isEqualTo(3.0);
        assertThat(enrolledAdmins()).isEqualTo(3);

        // The NIST cap arrives through authentication: the gauge sees it, the guard's enrolment count does not
        // (ADR-048, channel 2), so a disable that leaves two enrolled admins still passes.
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()), c.id());
        assertThat(registry.get(AuthenticableAdmins.GAUGE).gauge().value()).isEqualTo(2.0);
        assertThat(enrolledAdmins()).isEqualTo(3);

        disable(a, b.id()).andExpect(status().isOk());
        assertThat(registry.get(AuthenticableAdmins.GAUGE).gauge().value()).isEqualTo(1.0);
        assertThat(enrolledAdmins()).isEqualTo(2);
    }

    /**
     * Two admins disable each other at once, with three enrolled. Unlocked, each would read three, both would commit
     * and one admin would be left. Here the first holds the lock set until the second is seen waiting on it, then
     * commits; the second then reads two and is refused (ADR-048).
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void twoRacingDisablesCannotLeaveFewerThanTwoEnrolledAdmins() throws Exception {
        UUID a = enrolledAdmin().id();
        UUID b = enrolledAdmin().id();
        enrolledAdmin();
        CountDownLatch firstHoldsTheLocks = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> asAdmin(() ->
                transactions.executeWithoutResult(status -> {
                    assertThat(actions.setEnabled(a, b, false)).isPresent();
                    firstHoldsTheLocks.countDown();
                    await(releaseFirst);
                })));
        await(firstHoldsTheLocks);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> asAdmin(() ->
                actions.setEnabled(b, a, false)));

        awaitBlocked(second);
        releaseFirst.countDown();
        first.get();

        assertThatThrownBy(second::get).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOfSatisfying(AdminActionRefusedException.class, refused ->
                        assertThat(refused.reason()).isEqualTo(AdminRefusalReason.TWO_ADMIN_INVARIANT));
        assertThat(enabled(a)).isTrue();
        assertThat(enabled(b)).isFalse();
        assertThat(enrolledAdmins()).isEqualTo(2);
    }

    /**
     * Runs {@code action} as the controller would: inside a request, which the audit rows are scoped to, with an
     * {@code ADMIN} authentication, which the service's role check requires.
     */
    private static void asAdmin(Runnable action) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("PUT", "/api/admin/users/racing/enabled")));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "racing-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
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
