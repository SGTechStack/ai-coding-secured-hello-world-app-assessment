package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * Heavy contention on the guard's lock set (ADR-048): another transaction holds the subject's {@code users} row past
 * the database lock timeout, so the guarded change cannot take its locks. It is refused as retryable, 503
 * {@code SERVICE_BUSY} with an integer {@code Retry-After}, not a 500; nothing is written, and the refusal is audited
 * like the guard's own. Once the lock is released the same request succeeds. It runs in {@code ctx-lockhold}, so the
 * request waits out that context's full lock timeout once.
 */
class AdminLockContentionTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Proves("T-ADM-034")
    void aChangeWhoseLockSetTimesOutIsRefusedAsBusyAndChangesNothing() throws Exception {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
        Account subject = accounts.user();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", subject.id());
            held.countDown();
            await(release);
        }));
        try {
            await(held);
            try (AuditCapture audit = AuditCapture.start()) {
                disable(session, subject.id())
                        .andExpect(problem(ErrorCode.SERVICE_BUSY))
                        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
                assertThat(audit.withMessage("Administrative action refused.")).singleElement()
                        .satisfies(row -> assertThat(row)
                                .containsEntry("event.reason", "LOCK_TIMEOUT")
                                .containsEntry("user.id", admin.id().toString())
                                .containsEntry("user.target.id", subject.id().toString()));
                assertThat(audit.withMessage("Account disabled.")).isEmpty();
            }
        } finally {
            release.countDown();
            holder.get(20, TimeUnit.SECONDS);
        }
        assertThat(enabled(subject.id())).as("nothing was written").isTrue();

        disable(session, subject.id()).andExpect(status().isOk());
        assertThat(enabled(subject.id())).isFalse();
    }

    private ResultActions disable(CsrfSession session, UUID subject) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + subject + "/enabled").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
    }

    private boolean enabled(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, id));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).as("latch released").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
