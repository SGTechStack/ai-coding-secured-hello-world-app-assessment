package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.UserAccountRepository;

/**
 * An administrator's invite and a self-registration racing on one username and address (ADR-006; ADR-032). Whichever
 * commits first wins; the other answers as if it had run after it: the invite 201 or 400 {@code USER_EXISTS}, the
 * registration 202 or 400 {@code USERNAME_UNAVAILABLE}. Never a 500, and the address ends up with exactly one account.
 * Runs in {@code ctx-lockhold}, so the test can queue both behind a row lock it holds and release them together.
 */
class InviteRegistrationRaceTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Registrations registrations;

    @BeforeEach
    void setUp() {
        registrations = new Registrations(mockMvc);
    }

    private AdminCredentialCalls admin() throws Exception {
        return AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock),
                new Accounts(jdbc, passwordEncoder).withRole("ADMIN"));
    }

    /**
     * Both take the lapsed self-registration's row lock first: the invite to free its identifiers, the registration
     * to renew it. The test holds that lock until both are waiting on it.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Proves("T-CRED-031")
    void anInviteAndARegistrationQueuedOnALapsedRegistrationBothAnswerProperly() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        UUID lapsed = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        clock.advance(CredentialTokenType.ACTIVATION.lifetime());
        AdminCredentialCalls admin = admin();

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        assertThat(accounts.findForUpdateById(lapsed)).isPresent();
                        locked.countDown();
                        await(release);
                    }));
            locked.await();
            Future<MvcResult> invite = pool.submit(() -> admin.invite(username, email, "USER").andReturn());
            Future<MvcResult> registration = pool.submit(() -> registrations.register(username, email).andReturn());
            awaitBlocked(2, invite, registration);
            release.countDown();
            holder.get();

            MockHttpServletResponse invited = invite.get().getResponse();
            MockHttpServletResponse registered = registration.get().getResponse();
            assertProperOutcome(invited, registered, email);
            assertThat(invited.getStatus() == 201 ^ registered.getStatus() == 202)
                    .as("exactly one side wins: invite %s, registration %s", invited.getStatus(),
                            registered.getStatus())
                    .isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    /** Fresh identifiers, released together from a start gate, many times: the unique indexes decide the loser. */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Proves("T-CRED-031")
    void anInviteAndARegistrationOfNewIdentifiersReleasedTogetherBothAnswerProperly() throws Exception {
        AdminCredentialCalls admin = admin();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                String username = Registrations.freshUsername();
                String email = Registrations.emailFor(username);
                CountDownLatch start = new CountDownLatch(1);
                Future<MvcResult> invite = pool.submit(() -> {
                    await(start);
                    return admin.invite(username, email, "USER").andReturn();
                });
                Future<MvcResult> registration = pool.submit(() -> {
                    await(start);
                    return registrations.register(username, email).andReturn();
                });
                start.countDown();

                assertProperOutcome(invite.get().getResponse(), registration.get().getResponse(), email);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertProperOutcome(MockHttpServletResponse invite, MockHttpServletResponse registration,
            String email) throws Exception {
        assertThat(invite.getStatus()).as("the invite: %s", invite.getContentAsString()).isIn(201, 400);
        if (invite.getStatus() == 400) {
            assertThat(invite.getContentAsString()).contains("\"USER_EXISTS\"");
        }
        assertThat(registration.getStatus()).as("the registration: %s", registration.getContentAsString())
                .isIn(202, 400);
        if (registration.getStatus() == 400) {
            assertThat(registration.getContentAsString()).contains("\"USERNAME_UNAVAILABLE\"");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isOne();
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
