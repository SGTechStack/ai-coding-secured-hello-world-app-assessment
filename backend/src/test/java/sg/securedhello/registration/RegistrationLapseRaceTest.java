package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.user.UserAccountRepository;

/**
 * Two registrations that each take the other's lapsed username (ADR-032 amendment; T-CRED-027). Each names one lapsed
 * pending registration by its address and the other by its username, so taking the two row locks address-first would
 * take them in opposite orders and deadlock until the lock timeout, a 500. Runs in {@code ctx-lockhold}, so the test can
 * hold both rows while both registrations queue behind them, then release them together.
 */
class RegistrationLapseRaceTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Registrations registrations;

    @BeforeEach
    void setUp() {
        registrations = new Registrations(mockMvc);
    }

    private UUID idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    @Test
    @Proves("T-CRED-027")
    void twoRegistrationsThatEachTakeTheOthersLapsedUsernameNeverAnswer500() throws Exception {
        String firstName = Registrations.freshUsername();
        String firstEmail = Registrations.emailFor(firstName);
        String secondName = Registrations.freshUsername();
        String secondEmail = Registrations.emailFor(secondName);
        registrations.register(firstName, firstEmail).andExpect(status().isAccepted());
        registrations.register(secondName, secondEmail).andExpect(status().isAccepted());
        UUID first = idOf(firstEmail);
        UUID second = idOf(secondEmail);
        clock.advance(Registration.PENDING_PERIOD);
        // Any registration purges the expired holds, so neither crossing registration waits on the other's purge.
        String bystander = Registrations.freshUsername();
        registrations.register(bystander, Registrations.emailFor(bystander)).andExpect(status().isAccepted());

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (AuditCapture audit = AuditCapture.start()) {
            // The test holds both rows, so both registrations start, queue, and are released at once.
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        assertThat(accounts.findForUpdateById(first)).isPresent();
                        assertThat(accounts.findForUpdateById(second)).isPresent();
                        locked.countDown();
                        await(release);
                    }));
            locked.await();
            Future<MvcResult> crossOne = pool.submit(() -> registrations.register(secondName, firstEmail).andReturn());
            Future<MvcResult> crossTwo = pool.submit(() -> registrations.register(firstName, secondEmail).andReturn());
            awaitBlocked(2, crossOne, crossTwo);
            release.countDown();
            holder.get();

            assertThat(crossOne.get().getResponse().getStatus()).as("one crossing registration").isEqualTo(202);
            assertThat(crossTwo.get().getResponse().getStatus()).as("the other").isEqualTo(202);
            // Whichever order the steps ran in, the names have swapped addresses. A lapsed row renamed by its own
            // address's registration first is kept; one reached first by the other's lapse step is deleted and audited.
            assertThat(jdbc.queryForObject("SELECT username FROM users WHERE email = ?", String.class, firstEmail))
                    .isEqualTo(secondName);
            assertThat(jdbc.queryForObject("SELECT username FROM users WHERE email = ?", String.class, secondEmail))
                    .isEqualTo(firstName);
            assertThat(audit.withMessage("Lapsed pending registration deleted.")).isNotEmpty()
                    .allSatisfy(row -> assertThat(row.get("user.id")).isIn(first.toString(), second.toString()));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
