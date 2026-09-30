package local.builderday.auth.login.service;

import static local.builderday.support.AuditLogCapture.fields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import java.time.Duration;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.auth.login.service.LoginService.Outcome;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.Accounts;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** The Login attempt module through its one interface, against the real security and H2 store. */
@SpringBootTest(properties = {"app.security.login.rate-limit.attempts=10", "app.security.login.rate-limit.window=1m",
    "app.security.login.lockout.threshold=5", "app.security.login.lockout.duration=20m"})
@Import(TestClocks.class)
class LoginServiceTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final int RATE_LIMIT = 10;
  private static final int LOCKOUT_THRESHOLD = 5;

  @Autowired LoginService loginService;
  @Autowired TestClocks clocks;
  @Autowired UserRepository userRepository;
  @MockitoSpyBean PasswordEncoder passwordEncoder;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @MockitoSpyBean UserProfileService userProfileService;

  private UUID userId;

  // Other test contexts share this H2 database, and a bucket keeps the limit it was created with.
  @AfterEach
  void clearRateLimits() {
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
    userId = UUID.randomUUID();
    userRepository.save(new UserEntity(userId, "testuser123", null, passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_authenticate_when_usernameDiffersOnlyInCaseAndSurroundingSpace() {
    var request = anonymousRequest();

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      var outcome = loginService.attempt("  TestUser123 ", PASSWORD, request, new MockHttpServletResponse());

      assertThat(outcome).isInstanceOfSatisfying(Outcome.Authenticated.class,
          authenticated -> assertThat(authenticated.profile().username()).isEqualTo("testuser123"));
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event)).containsEntry("event.action", "user-login").containsEntry("event.reason", "success")
            .containsEntry("event.outcome", "success").containsEntry("user.id", userId.toString());
      });
    }
    assertThat(request.getSession(false)).isNotNull();
    assertThat(request.getSession(false).getAttribute(contextKey())).isNotNull();
  }

  @Test
  void should_lockSilentlyAndRejectEvenTheCorrectPassword_when_failuresReachTheThreshold() {
    for (int attempt = 1; attempt < LOCKOUT_THRESHOLD; attempt++) {
      assertThat(attempt("wrong")).isInstanceOf(Outcome.InvalidCredentials.class);
    }

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      assertThat(attempt("wrong")).as("failure that sets the lock").isInstanceOf(Outcome.InvalidCredentials.class);
      assertThat(attempt(PASSWORD)).as("correct password while locked").isInstanceOf(Outcome.InvalidCredentials.class);

      assertThat(audit.events())
          .extracting(event -> fields(event).get("event.action") + " " + fields(event).get("event.reason"))
          .containsExactly("account-lockout consecutive_failures", "user-login invalid_credentials",
              "user-login locked");
      assertThat(audit.events()).allSatisfy(event -> assertThat(fields(event))
          .containsEntry("user.id", userId.toString()).containsEntry("source.ip", "127.0.0.1"));
    }
  }

  @Test
  void should_neitherCountNorExtendTheLock_when_attemptsArriveDuringIt() {
    for (int attempt = 0; attempt < LOCKOUT_THRESHOLD; attempt++) attempt("wrong");

    clocks.advance(Duration.ofMinutes(10));
    for (int attempt = 1; attempt < LOCKOUT_THRESHOLD; attempt++) {
      assertThat(attempt("wrong")).as("failure during the lock").isInstanceOf(Outcome.InvalidCredentials.class);
    }
    clocks.advance(Duration.ofMinutes(10));

    assertThat(attempt("wrong")).as("first failure after the lock expired")
        .isInstanceOf(Outcome.InvalidCredentials.class);
    assertThat(attempt(PASSWORD)).as("20 minutes after the lock was set").isInstanceOf(Outcome.Authenticated.class);
  }

  @Test
  void should_holdTheLockForItsFullDuration_when_theCorrectPasswordArrivesJustBeforeItEnds() {
    for (int attempt = 0; attempt < LOCKOUT_THRESHOLD; attempt++) attempt("wrong");

    clocks.advance(Duration.ofMinutes(20).minusSeconds(5));
    assertThat(attempt(PASSWORD)).as("5 seconds before the lock ends").isInstanceOf(Outcome.InvalidCredentials.class);

    clocks.advance(Duration.ofSeconds(5));
    assertThat(attempt(PASSWORD)).as("exactly when the lock ends").isInstanceOf(Outcome.Authenticated.class);
  }

  @Test
  void should_countEveryFailure_when_failuresArriveConcurrently() throws Exception {
    int concurrent = LOCKOUT_THRESHOLD - 1;
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(concurrent)) {
      var failures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int attempt = 0; attempt < concurrent; attempt++) {
        failures.add(pool.submit(() -> {
          start.await();
          return userProfileService.recordFailedLogin("testuser123", clocks.now());
        }));
      }
      start.countDown();
      for (var failure : failures) {
        assertThat(failure.get()).as("no concurrent failure below the threshold locks").isFalse();
      }
    }

    // Had a concurrent increment been lost, this next failure would not reach the threshold.
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      attempt("wrong");

      assertThat(audit.events()).extracting(event -> fields(event).get("event.action")).contains("account-lockout");
    }
    assertThat(attempt(PASSWORD)).as("locked").isInstanceOf(Outcome.InvalidCredentials.class);
  }

  @Test
  void should_resetTheFailureCount_when_loginSucceeds() {
    for (int attempt = 1; attempt < LOCKOUT_THRESHOLD; attempt++) attempt("wrong");
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);

    for (int attempt = 1; attempt < LOCKOUT_THRESHOLD; attempt++) attempt("wrong");
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);
  }

  @Test
  void should_neverLockOrError_when_theUsernameIsUnknown() {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      for (int attempt = 0; attempt <= LOCKOUT_THRESHOLD; attempt++) {
        assertThat(loginService.attempt("nobody123", "wrong", anonymousRequest(), new MockHttpServletResponse()))
            .isInstanceOf(Outcome.InvalidCredentials.class);
      }

      assertThat(audit.events()).allSatisfy(event -> assertThat(fields(event))
          .containsEntry("event.action", "user-login").containsEntry("event.reason", "invalid_credentials"));
    }
  }

  @Test
  void should_rateLimitTheSourceIp_when_itExceedsTheAttemptLimitWhateverTheOutcomes() {
    for (int attempt = 0; attempt < 4; attempt++) {
      assertThat(attempt("wrong")).isInstanceOf(Outcome.InvalidCredentials.class);
    }
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);
    for (int attempt = 0; attempt < 3; attempt++) {
      assertThat(loginService.attempt("nobody123", PASSWORD, anonymousRequest(), new MockHttpServletResponse()))
          .isInstanceOf(Outcome.InvalidCredentials.class);
    }
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);
    assertThat(attempt(PASSWORD)).as("10th attempt").isInstanceOf(Outcome.Authenticated.class);

    clearInvocations(passwordEncoder);
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      assertThat(attempt(PASSWORD)).as("11th attempt, correct password").isInstanceOf(Outcome.RateLimited.class);

      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(event)).containsEntry("event.action", "user-login")
            .containsEntry("event.reason", "rate_limited");
      });
    }
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void should_allowAttemptsAgain_when_theRateLimitWindowRollsOver_andSuccessNeverResetsIt() {
    for (int attempt = 0; attempt < RATE_LIMIT; attempt++) {
      assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);
    }
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.RateLimited.class);

    clocks.advance(Duration.ofMinutes(1));

    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.Authenticated.class);
  }

  @Test
  void should_stillAuthenticate_when_anotherSourceIpIsRateLimited() {
    for (int attempt = 0; attempt < RATE_LIMIT; attempt++) attempt(PASSWORD);
    assertThat(attempt(PASSWORD)).isInstanceOf(Outcome.RateLimited.class);

    var otherSource = anonymousRequest();
    otherSource.setRemoteAddr("203.0.113.7");
    assertThat(loginService.attempt("testuser123", PASSWORD, otherSource, new MockHttpServletResponse()))
        .isInstanceOf(Outcome.Authenticated.class);
  }

  @Test
  void should_rejectLikeAWrongPasswordAfterThePasswordCheck_when_theAccountIsDisabled() {
    var user = userRepository.findById(userId).orElseThrow();
    Accounts.disable(user, clocks.now());
    userRepository.save(user);

    assertThatRejectedAfterPasswordCheck(PASSWORD, "disabled");
    assertThatRejectedAfterPasswordCheck("wrong", "invalid_credentials");
  }

  @Test
  void should_rejectLikeAWrongPasswordAfterThePasswordCheck_when_theAccountIsDeleted() {
    var user = userRepository.findById(userId).orElseThrow();
    Accounts.markDeleted(user, clocks.now());
    userRepository.save(user);

    assertThatRejectedAfterPasswordCheck(PASSWORD, "disabled");
    assertThatRejectedAfterPasswordCheck("wrong", "invalid_credentials");
  }

  @Test
  void should_checkAPasswordAgainstADummyHash_when_theUsernameIsUnknown() {
    clearInvocations(passwordEncoder);
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      assertThat(loginService.attempt("nobody123", PASSWORD, anonymousRequest(), new MockHttpServletResponse()))
          .isInstanceOf(Outcome.InvalidCredentials.class);

      assertThat(audit.events()).singleElement()
          .satisfies(event -> assertThat(fields(event)).containsEntry("event.reason", "invalid_credentials"));
    }
    verify(passwordEncoder).matches(eq(PASSWORD), any());
  }

  @Test
  void should_takeAsLongAsAWrongPassword_when_theAttemptIsRejectedForAnyOtherReason() {
    var disabled = new UserEntity(UUID.randomUUID(), "disabled123", null, passwordEncoder.encode(PASSWORD),
        "USER", true);
    Accounts.disable(disabled, clocks.now());
    userRepository.save(disabled);
    userRepository.save(new UserEntity(UUID.randomUUID(), "locked123", null, passwordEncoder.encode(PASSWORD),
        "USER", true));
    for (int attempt = 0; attempt < LOCKOUT_THRESHOLD; attempt++) timedAttempt("locked123", "wrong");
    timedAttempt("testuser123", PASSWORD); // warm-up

    long wrongPassword = medianNanos("testuser123", "wrong");

    // A short-circuit that skipped BCrypt would be about a hundredth of this; the band only absorbs scheduling noise.
    assertThat(medianNanos("nobody123", PASSWORD)).as("unknown username")
        .isBetween(wrongPassword / 2, wrongPassword * 2);
    assertThat(medianNanos("locked123", PASSWORD)).as("locked account, correct password")
        .isBetween(wrongPassword / 2, wrongPassword * 2);
    assertThat(medianNanos("disabled123", PASSWORD)).as("disabled account, correct password")
        .isBetween(wrongPassword / 2, wrongPassword * 2);
  }

  private long medianNanos(String username, String password) {
    long[] samples = new long[5];
    for (int sample = 0; sample < samples.length; sample++) samples[sample] = timedAttempt(username, password);
    java.util.Arrays.sort(samples);
    return samples[samples.length / 2];
  }

  /** One rejected attempt from a fresh source IP, so the Login rate limit never interferes. */
  private long timedAttempt(String username, String password) {
    var request = anonymousRequest();
    request.setRemoteAddr("198.51.100." + nextSourceIp++);
    long start = System.nanoTime();
    loginService.attempt(username, password, request, new MockHttpServletResponse());
    return System.nanoTime() - start;
  }

  private int nextSourceIp = 1;

  private void assertThatRejectedAfterPasswordCheck(String password, String auditReason) {
    clearInvocations(passwordEncoder);
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      assertThat(attempt(password)).isInstanceOf(Outcome.InvalidCredentials.class);

      assertThat(audit.events()).singleElement()
          .satisfies(event -> assertThat(fields(event)).containsEntry("event.reason", auditReason));
    }
    verify(passwordEncoder).matches(eq(password), any());
  }

  @Test
  void should_persistNoSession_when_aLookupFailsAfterAuthentication() {
    doThrow(new IllegalStateException("database unavailable")).when(userProfileService)
        .recordSuccessfulLogin(anyString(), any());
    var request = anonymousRequest();
    var anonymousSession = request.getSession(false);

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      assertThatThrownBy(() -> loginService.attempt("testuser123", PASSWORD, request, new MockHttpServletResponse()))
          .hasMessage("database unavailable");

      assertThat(audit.events()).singleElement()
          .satisfies(event -> assertThat(fields(event)).containsEntry("event.reason", "system_error"));
    }
    assertThat(request.getSession(false)).isSameAs(anonymousSession);
    assertThat(anonymousSession.getAttribute(contextKey())).isNull();
  }

  private Outcome attempt(String password) {
    return loginService.attempt("testuser123", password, anonymousRequest(), new MockHttpServletResponse());
  }

  private static MockHttpServletRequest anonymousRequest() {
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setSession(new MockHttpSession());
    return request;
  }

  private static String contextKey() {
    return HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
  }
}
