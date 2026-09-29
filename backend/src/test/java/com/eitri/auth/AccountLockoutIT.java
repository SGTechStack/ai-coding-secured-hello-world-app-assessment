package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account-lockout;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(AccountLockoutIT.ClockConfiguration.class)
class AccountLockoutIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant LOCK_DEADLINE = NOW.plus(Duration.ofMinutes(15));
    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_HASH_KEY = "test-only-session-hash-placeholder-not-for-production";
    private static final String GENERIC_FAILURE =
            "{\"message\":\"Invalid username or password\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetAccountAndClock() {
        clock.set(NOW);
        jdbc.update(
                "UPDATE users SET enabled = TRUE, failed_login_attempts = 0, locked_until = NULL, "
                        + "failed_login_window_started_at = NULL WHERE username = ?",
                "johndoe");
    }

    @Test
    void failuresWithinTheWindowOfTheFirstFailureLockTheAccount() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertGenericFailure(login("johndoe", "WrongPassword!"));
        }
        assertThat(windowStartedAt()).isEqualTo(NOW);
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));

        assertGenericFailure(login("johndoe", "WrongPassword!"));

        assertThat(failedLoginCount()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("[assessment/story3-ac8] failures spread over longer than the lockout window do not lock the account")
    void failuresOutsideTheWindowStartANewRun() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertGenericFailure(login("johndoe", "WrongPassword!"));
        }
        clock.advance(Duration.ofMinutes(15));

        assertGenericFailure(login("johndoe", "WrongPassword!"));

        assertThat(failedLoginCount()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
        assertThat(windowStartedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("[assessment/story3-ac9] after the cooldown a single wrong password starts a new count")
    void wrongPasswordAfterTheCooldownStartsANewRun() throws Exception {
        setAccountState(true, 5, LOCK_DEADLINE);
        jdbc.update(
                "UPDATE users SET failed_login_window_started_at = ? WHERE username = ?",
                Timestamp.from(NOW),
                "johndoe");
        clock.advance(Duration.ofMinutes(15));

        assertGenericFailure(login("johndoe", "WrongPassword!"));

        assertThat(failedLoginCount()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void anExpiredLockStartsANewRunEvenWhenTheWindowIsLongerThanTheLock() throws Exception {
        // A run that began inside the window but whose lock has already expired must not re-lock at once.
        setAccountState(true, 5, NOW.minusSeconds(1));
        jdbc.update(
                "UPDATE users SET failed_login_window_started_at = ? WHERE username = ?",
                Timestamp.from(NOW.minus(Duration.ofMinutes(1))),
                "johndoe");

        assertGenericFailure(login("johndoe", "WrongPassword!"));

        assertThat(failedLoginCount()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void successfulLoginClearsTheWindow() throws Exception {
        assertGenericFailure(login("johndoe", "WrongPassword!"));
        assertThat(windowStartedAt()).isNotNull();

        assertThat(login("johndoe", "Password123!")).hasStatusOk();

        assertThat(windowStartedAt()).isNull();
    }

    @Test
    void fifthConsecutiveFailureLocksTheAccountForTheConfiguredDuration() throws Exception {
        for (int attempt = 1; attempt < 5; attempt++) {
            assertGenericFailure(login("johndoe", "wrong-password-" + attempt));
            assertThat(failedLoginCount()).isEqualTo(attempt);
            assertThat(lockedUntil()).isNull();
        }

        assertGenericFailure(login("johndoe", "wrong-password-5"));

        assertThat(failedLoginCount()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(LOCK_DEADLINE);
    }

    @Test
    void activeLockAndDisabledAccountReturnTheSameGenericResponseWithoutGrowingLockedCounter() throws Exception {
        setAccountState(true, 5, LOCK_DEADLINE);
        String lockedWrongPassword = body(login("johndoe", "wrong-while-locked"));
        String lockedCorrectPassword = body(login("johndoe", "Password123!"));

        assertThat(lockedWrongPassword).isEqualTo(GENERIC_FAILURE);
        assertThat(lockedCorrectPassword).isEqualTo(GENERIC_FAILURE);
        assertThat(failedLoginCount()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(LOCK_DEADLINE);

        setAccountState(false, 0, null);
        assertThat(body(login("johndoe", "wrong-while-disabled"))).isEqualTo(GENERIC_FAILURE);
        for (int attempt = 0; attempt < 5; attempt++) {
            String disabled = body(login("johndoe", "Password123!"));
            assertThat(disabled).isEqualTo(GENERIC_FAILURE);
        }
        assertThat(failedLoginCount()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    @DisplayName("[assessment/story3-ac2] a successful login before the threshold resets the consecutive failure count")
    void successBeforeTheThresholdResetsTheConsecutiveCount() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertGenericFailure(login("johndoe", "WrongPassword!"));
        }

        assertThat(login("johndoe", "Password123!")).hasStatusOk();
        assertGenericFailure(login("johndoe", "WrongPassword!"));

        assertThat(failedLoginCount()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
    }

    @Test
    @DisplayName("[assessment/story3-ac3] after the cooldown the correct password succeeds and the counter resets")
    void lockLiftsAutomaticallyAtTheConfiguredDeadline() throws Exception {
        setAccountState(true, 5, LOCK_DEADLINE);
        clock.advance(Duration.ofMinutes(15));

        assertThat(login("johndoe", "Password123!")).hasStatusOk();
        assertThat(failedLoginCount()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsPriorFailuresAndClearsAnExpiredLock() throws Exception {
        setAccountState(true, 4, NOW.minusSeconds(1));

        assertThat(login("johndoe", "Password123!")).hasStatusOk();
        assertThat(failedLoginCount()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void unknownAccountFailureDoesNotCreateOrMutateAccountState() throws Exception {
        int accountCountBefore = accountCount();

        assertGenericFailure(login("missing-account", "wrong-password"));

        assertThat(accountCount()).isEqualTo(accountCountBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "missing-account"))
                .isZero();
        assertThat(failedLoginCount()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    @DisplayName("[assessment/story3-ac1] the fifth consecutive failure locks the account for 15 minutes and is audited")
    void accountLockoutWritesWarnAuditWithSafeFieldsAndPreservesFailureAudit() throws Exception {
        setAccountState(true, 4, null);
        Session session = SessionClient.fetchCsrf(mvc);
        String sessionId = session.sessionId();
        String password = "fifth-failure-sensitive-canary";

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            assertGenericFailure(session.login(
                    mvc, "{\"username\":\"johndoe\",\"password\":\"" + password + "\"}"));

            String lockout = audit.line("Account locked after failed authentication attempts");
            assertThat(JsonPath.<String>read(lockout, "$.log.level")).isEqualTo("WARN");
            assertThat(JsonPath.<String>read(lockout, "$.log.logger")).isEqualTo("AUDIT");
            assertThat(JsonPath.<String>read(lockout, "$.event.kind")).isEqualTo("event");
            assertThat(JsonPath.<String>read(lockout, "$.event.category")).isEqualTo("authentication");
            assertThat(JsonPath.<String>read(lockout, "$.event.action")).isEqualTo("account-lockout");
            assertThat(JsonPath.<String>read(lockout, "$.event.outcome")).isEqualTo("failure");
            assertThat(JsonPath.<String>read(lockout, "$.user.id")).isEqualTo(ACCOUNT_ID);
            assertThat(JsonPath.<String>read(lockout, "$.user.name")).isEqualTo("johndoe");
            assertThat(JsonPath.<String>read(lockout, "$.session.hash")).isEqualTo(hmac(sessionId));
            assertThat(lockout)
                    .doesNotContain(password, session.token(), sessionId)
                    .doesNotContain("\"username\"", "\"password\"");

            String failed = audit.line("User authentication failed");
            assertThat(JsonPath.<String>read(failed, "$.event.action")).isEqualTo("user-authentication");
            assertThat(JsonPath.<String>read(failed, "$.event.outcome")).isEqualTo("failure");
            assertThat(JsonPath.<String>read(failed, "$.user.id")).isEqualTo(ACCOUNT_ID);
            assertThat(JsonPath.<String>read(failed, "$.user.name")).isEqualTo("johndoe");
        }
        assertThat(lockedUntil()).isEqualTo(LOCK_DEADLINE);
    }

    @Test
    void simultaneousFailuresAreSerializedWithoutLostUpdates() throws Exception {
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sessions.add(SessionClient.fetchCsrf(mvc));
        }

        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Future<MvcTestResult>> attempts = new ArrayList<>();
            for (Session session : sessions) {
                attempts.add(executor.submit(() -> loginTogether(session, ready, start)));
            }
            ready.await();
            start.countDown();
            for (Future<MvcTestResult> attempt : attempts) {
                assertGenericFailure(attempt.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(failedLoginCount()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(LOCK_DEADLINE);
    }

    private MvcTestResult login(String username, String password) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        return session.login(
                mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private MvcTestResult loginTogether(Session session, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return session.login(
                mvc, "{\"username\":\"johndoe\",\"password\":\"concurrent-wrong-password\"}");
    }

    private static void assertGenericFailure(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(401);
        assertThat(body(result)).isEqualTo(GENERIC_FAILURE);
    }

    private static String body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(401);
        return result.getResponse().getContentAsString();
    }

    private int failedLoginCount() {
        return jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE username = ?", Integer.class, "johndoe");
    }

    private Instant lockedUntil() {
        Timestamp value = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE username = ?", Timestamp.class, "johndoe");
        return value == null ? null : value.toInstant();
    }

    private Instant windowStartedAt() {
        Timestamp value = jdbc.queryForObject(
                "SELECT failed_login_window_started_at FROM users WHERE username = ?", Timestamp.class, "johndoe");
        return value == null ? null : value.toInstant();
    }

    private int accountCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    private void setAccountState(boolean enabled, int failedCount, Instant lockedUntil) {
        jdbc.update(
                "UPDATE users SET enabled = ?, failed_login_attempts = ?, locked_until = ? WHERE username = ?",
                enabled,
                failedCount,
                lockedUntil == null ? null : Timestamp.from(lockedUntil),
                "johndoe");
    }

    private static String hmac(String sessionId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        MutableClock accountLockoutTestClock() {
            return new MutableClock(NOW);
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
