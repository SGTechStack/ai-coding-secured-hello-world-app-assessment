package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.user.PasswordLockoutState;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The password lockout, its ladder and the NIST cap through the real sign-in (ADR-011; ADR-012; ADR-013). Every value
 * is read from the bound {@link LockoutProperties}, never restated. Each test uses its own accounts, and moves the
 * shared clock only forward.
 */
class LockoutTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LOOPBACK = "127.0.0.1";
    private static final String WRONG = "not-the-password-at-all";

    private static final String LOGIN_FAILED = "Login failed.";
    private static final String LOCKED = "Account locked.";
    private static final String LOCK_CLEARED = "Account lock cleared.";
    private static final String ALERT = "Password failures reached the alert threshold.";
    private static final String DISABLED = "Password disabled.";

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationEventPublisher events;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        // Onto a whole microsecond, the columns' precision, so a lock read back is exactly the rung from now.
        clock.advance(Duration.ofNanos(1_000 - clock.instant().getNano() % 1_000));
    }

    private MvcResult login(String source, Account account, String password) throws Exception {
        return SignedIn.loginFrom(mockMvc, source, account.username(), password).andReturn();
    }

    private void fail(Account account) throws Exception {
        assertThat(login(LOOPBACK, account, WRONG).getResponse().getStatus()).isEqualTo(401);
    }

    private void fail(Account account, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            fail(account);
        }
    }

    private PasswordLockoutState state(Account account) {
        return accounts.lockoutState(account);
    }

    private static String bodyWithoutTraceId(MvcResult result) throws Exception {
        ObjectNode body = (ObjectNode) JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        body.remove("traceId");
        return body.toString();
    }

    private static List<Map<String, Object>> rowsFor(AuditCapture audit, String message, Account account) {
        return audit.withMessage(message).stream()
                .filter(row -> account.id().toString().equals(row.get("user.id")))
                .toList();
    }

    @Test
    @Proves({"T-LCK-004", "T-LCK-002", "T-LCK-006"})
    void theThresholdLocksTheCorrectPasswordGetsTheSame401AndTheLockLiftsOnItsOwn() throws Exception {
        Account alice = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            fail(alice, lockout.threshold() - 1);
            assertThat(state(alice).lockedUntil()).as("one short of the threshold").isNull();
            assertThat(rowsFor(audit, LOCKED, alice)).isEmpty();

            fail(alice);
            PasswordLockoutState locked = state(alice);
            assertThat(locked.lockedUntil()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS)
                    .plus(lockout.ladder().rungs().getFirst()));
            assertThat(rowsFor(audit, LOCKED, alice)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("log.level", "WARN")
                    .containsEntry("event.reason", "THRESHOLD_REACHED")
                    .containsEntry("event.action", "user-authentication"));

            MvcResult wrong = login(LOOPBACK, alice, WRONG);
            MvcResult right = login(LOOPBACK, alice, alice.password());
            assertThat(right.getResponse().getStatus()).isEqualTo(401);
            assertThat(right.getResponse().getContentAsString()).contains(ErrorCode.AUTHENTICATION_FAILED.name());
            assertThat(bodyWithoutTraceId(right)).isEqualTo(bodyWithoutTraceId(wrong));
            assertThat(right.getResponse().getCookie("SESSION")).as("no session was authenticated").isNull();
            assertThat(state(alice)).as("attempts during a lock move no counter").isEqualTo(locked);
            assertThat(rowsFor(audit, LOGIN_FAILED, alice)).last()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "ACCOUNT_LOCKED"));

            clock.advance(Duration.between(clock.instant(), locked.lockedUntil()).minusSeconds(1));
            assertThat(login(LOOPBACK, alice, alice.password()).getResponse().getStatus()).as("a second early")
                    .isEqualTo(401);

            clock.advance(Duration.ofSeconds(1));
            assertThat(login(LOOPBACK, alice, alice.password()).getResponse().getStatus()).as("lifted").isEqualTo(200);
            assertThat(state(alice)).as("the first success after the lift resets both counters")
                    .isEqualTo(new PasswordLockoutState(0, null, null, 0, null));
            assertThat(rowsFor(audit, LOCK_CLEARED, alice)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "AUTO_LIFT"));
        }
    }

    @Test
    @Proves("T-LCK-006")
    void aSuccessResetsTheCounterSoAsManyFailuresAgainDoNotLock() throws Exception {
        Account bob = accounts.user();
        int belowThreshold = lockout.threshold() - 1;
        fail(bob, belowThreshold);
        assertThat(state(bob).failedLoginAttempts()).isEqualTo(belowThreshold);

        assertThat(login(LOOPBACK, bob, bob.password()).getResponse().getStatus()).isEqualTo(200);
        assertThat(state(bob).failedLoginAttempts()).isZero();
        assertThat(state(bob).consecutiveFailuresSinceSuccess()).isZero();

        fail(bob, belowThreshold);
        assertThat(state(bob).lockedUntil()).isNull();
        assertThat(login(LOOPBACK, bob, bob.password()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @Proves({"T-LCK-001", "T-LCK-013"})
    void failuresFurtherApartThanTheWindowDoNotAccumulateNotEvenRightAfterALift() throws Exception {
        Account carol = accounts.user();
        Duration window = lockout.observationWindow();
        for (int i = 0; i < lockout.threshold() + 1; i++) {
            fail(carol);
            assertThat(state(carol).failedLoginAttempts()).as("spaced failure %d", i + 1).isEqualTo(1);
            clock.advance(window);
        }
        assertThat(state(carol).lockedUntil()).isNull();
        assertThat(state(carol).consecutiveFailuresSinceSuccess()).as("the cap counter has no window")
                .isEqualTo(lockout.threshold() + 1);

        for (int i = 0; i < lockout.threshold(); i++) {
            if (i > 0) {
                clock.advance(window.minusSeconds(1));
            }
            fail(carol);
        }
        Instant lockedUntil = state(carol).lockedUntil();
        assertThat(lockedUntil).as("the same count inside the window locks").isNotNull();

        clock.advance(Duration.between(clock.instant(), lockedUntil));
        fail(carol);
        assertThat(state(carol).failedLoginAttempts()).as("the first failure after the lift").isEqualTo(1);
        assertThat(state(carol).lockedUntil()).as("and it does not re-lock").isNull();
    }

    @Test
    @Proves("T-LCK-003")
    void failuresFromDistinctSourcesLockTheAccount() throws Exception {
        Account dan = accounts.user();
        for (int i = 1; i <= lockout.threshold(); i++) {
            assertThat(login("192.0.2." + i, dan, WRONG).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(state(dan).lockedAt(clock.instant())).isTrue();
        assertThat(login("192.0.2.200", dan, dan.password()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @Proves({"T-LCK-020", "T-LCK-010", "T-LCK-014", "T-LCK-015", "T-LCK-016"})
    void aSteadyAttackDisablesNoSoonerThanTheFloorAndTheAlertComesFirst() throws Exception {
        Account target = accounts.user();
        LockoutLadder ladder = lockout.toLadder();
        Instant start = clock.instant();
        List<Duration> locks = new ArrayList<>();
        Instant alertAt = null;
        int failures = 0;
        try (AuditCapture audit = AuditCapture.start()) {
            while (state(target).passwordDisabledAt() == null) {
                for (int i = 0; i < lockout.threshold() && state(target).passwordDisabledAt() == null; i++) {
                    fail(target);
                    failures++;
                    if (alertAt == null && !rowsFor(audit, ALERT, target).isEmpty()) {
                        alertAt = clock.instant();
                        assertThat(failures).as("the alert's failure").isEqualTo(lockout.nist().alertThreshold());
                    }
                }
                Instant lockedUntil = state(target).lockedUntil();
                if (lockedUntil != null) {
                    Duration lock = Duration.between(clock.instant(), lockedUntil);
                    locks.add(lock);
                    clock.advance(lock);
                }
            }
            Instant disabledAt = clock.instant();

            assertThat(failures).isEqualTo(lockout.nist().cap());
            assertThat(state(target).passwordDisabledAt()).isEqualTo(disabledAt);
            assertThat(locks).as("the ladder: each rung for its cycles, then the last rung")
                    .isEqualTo(expectedLocks(ladder));
            assertThat(Duration.between(start, disabledAt)).isGreaterThanOrEqualTo(LockoutLadder.FLOOR_TO_DISABLE);
            assertThat(alertAt).isNotNull().isBefore(disabledAt);
            assertThat(Duration.between(alertAt, disabledAt)).isGreaterThanOrEqualTo(LockoutLadder.FLOOR_WARNING);

            assertThat(rowsFor(audit, ALERT, target)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("log.level", "WARN").containsEntry("event.severity", "high"));
            assertThat(rowsFor(audit, DISABLED, target)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("log.level", "ERROR")
                    .containsEntry("event.severity", "critical")
                    .containsEntry("event.reason", "FAILURE_CAP"));
            assertThat(rowsFor(audit, LOCKED, target)).hasSize(locks.size());
            assertThat(rowsFor(audit, LOCK_CLEARED, target)).hasSize(locks.size());
        }
    }

    private static List<Duration> expectedLocks(LockoutLadder ladder) {
        List<Duration> expected = new ArrayList<>();
        for (int lock = 1; lock <= ladder.locksBeforeCap(); lock++) {
            expected.add(ladder.lockDuration(lock));
        }
        return expected;
    }

    @Test
    @Proves("T-LCK-009")
    void aCappedPasswordIsRefusedBeforeTheComparisonAndTheRefusalMovesNoCounter() throws Exception {
        assertThat(ReflectionTestUtils.getField(events, "defaultAuthenticationFailureEventConstructor"))
                .as("no default failure event, so an unmapped exception publishes nothing").isNull();

        Account erin = accounts.user();
        jdbc.update("UPDATE users SET consecutive_failures_since_success = ? WHERE id = ?",
                lockout.nist().cap() - 1, erin.id());
        fail(erin);
        PasswordLockoutState capped = state(erin);
        assertThat(capped.passwordDisabled()).isTrue();

        try (AuditCapture audit = AuditCapture.start()) {
            MvcResult wrong = login(LOOPBACK, erin, WRONG);
            MvcResult right = login(LOOPBACK, erin, erin.password());
            assertThat(right.getResponse().getStatus()).isEqualTo(401);
            assertThat(bodyWithoutTraceId(right)).isEqualTo(bodyWithoutTraceId(wrong));
            assertThat(state(erin)).isEqualTo(capped);
            assertThat(rowsFor(audit, LOGIN_FAILED, erin)).as("one row per attempt, from the handler, none from an event")
                    .hasSize(2)
                    .allSatisfy(row -> assertThat(row).containsEntry("event.reason", "PASSWORD_DISABLED"));
        }

        clock.advance(Duration.ofDays(2));
        assertThat(login(LOOPBACK, erin, erin.password()).getResponse().getStatus()).as("only rebinding clears it")
                .isEqualTo(401);
    }

    @Test
    void failuresBelowTheThresholdLeaveTheOwnersLiveSessionAlone() throws Exception {
        Account frank = accounts.user();
        CsrfSession live = SignedIn.as(mockMvc, frank);

        fail(frank, lockout.threshold() - 1);

        assertThat(state(frank).failedLoginAttempts()).isEqualTo(lockout.threshold() - 1);
        mockMvc.perform(get("/api/hello").cookie(live.cookie())).andExpect(status().isOk());
    }

    @Test
    void anUnknownUsernameOrADisabledAccountMovesNoCounter() throws Exception {
        Account disabled = accounts.disabled();
        for (int i = 0; i < lockout.threshold(); i++) {
            SignedIn.loginFrom(mockMvc, LOOPBACK, disabled.username(), WRONG)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            SignedIn.loginFrom(mockMvc, LOOPBACK, Accounts.unknownUsername(), WRONG)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        }
        assertThat(state(disabled)).isEqualTo(new PasswordLockoutState(0, null, null, 0, null));
    }

    /**
     * Concurrent wrong passwords are counted one after another under the row lock: none is lost, and those that land
     * after the lock are not counted. T-LCK-008 itself asks for BCrypt cost 12 on the non-dev posture; this runs the
     * shared context's cost.
     */
    @Test
    void concurrentFailuresAreCountedUnderTheRowLock() throws Exception {
        Account grace = accounts.user();
        int attempts = 2 * lockout.threshold();
        List<CsrfSession> sessions = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            sessions.add(CsrfSession.bootstrap(mockMvc));
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (CsrfSession session : sessions) {
                done.add(pool.submit(() -> {
                    start.await();
                    statuses.add(SignedIn.login(mockMvc, session, grace.username(), WRONG).andReturn().getResponse()
                            .getStatus());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : done) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).hasSize(attempts).containsOnly(401);
        PasswordLockoutState after = state(grace);
        assertThat(after.failedLoginAttempts()).isEqualTo(lockout.threshold());
        assertThat(after.consecutiveFailuresSinceSuccess()).isEqualTo(lockout.threshold());
        assertThat(after.lockedAt(clock.instant())).isTrue();
    }
}
