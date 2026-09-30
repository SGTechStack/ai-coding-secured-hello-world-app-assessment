package org.eds.demo.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Progressive backoff per Account and the per-IP throttle, driven by a controllable clock. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:sign-in-backoff-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.admin.password=test-only-backoff-admin-secret",
      "app.sign-in.ip-max-failures=" + SignInBackoffIT.IP_MAX_FAILURES
    })
class SignInBackoffIT {

  static final int IP_MAX_FAILURES = 5;

  private static final String PASSWORD = "test-only-backoff-secret";
  private static final String WRONG_PASSWORD = "not-the-password";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";

  /** Default backoff threshold: the third consecutive failure starts the first delay. */
  private static final int THRESHOLD = 3;

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void correctPasswordDuringTheDelayIsRefusedWithTheSameResponseAsAnyFailure() throws Exception {
    var username = account("during-delay");
    var ip = "10.0.1.1";
    var ordinaryFailure = failure(username, WRONG_PASSWORD, ip);
    failure(username, WRONG_PASSWORD, ip);
    failure(username, WRONG_PASSWORD, ip); // third failure starts a 1 s delay

    var duringDelay = failure(username, PASSWORD, ip);

    assertThat(duringDelay).isEqualTo(ordinaryFailure);
    assertThat(failure("nobody-by-that-name", WRONG_PASSWORD, ip)).isEqualTo(ordinaryFailure);
  }

  @Test
  void correctPasswordSucceedsOnceTheDelayHasElapsedAndResetsTheCounter() throws Exception {
    var username = account("recovery");
    var ip = "10.0.2.1";
    for (int i = 0; i < THRESHOLD; i++) {
      failure(username, WRONG_PASSWORD, ip);
    }
    clock.advance(Duration.ofMillis(999));
    failure(username, PASSWORD, ip);
    clock.advance(Duration.ofMillis(1));

    mockMvc.perform(signIn(username, PASSWORD, ip)).andExpect(status().isOk());

    var stored = appUserRepository.findByUsername(username).orElseThrow();
    assertThat(stored.getFailedLoginAttempts()).isZero();
    assertThat(stored.getLockedUntil()).isNull();
  }

  @Test
  void delayDoublesPerFailureAndStopsGrowingAtTheCap() throws Exception {
    var username = account("growth");
    // A fresh address per attempt keeps the per-IP throttle out of the way.
    var attempt = 0;
    var expectedSeconds = new long[] {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 900, 900};
    for (int i = 0; i < THRESHOLD - 1; i++) {
      failure(username, WRONG_PASSWORD, "10.0.3." + attempt++);
    }

    for (long seconds : expectedSeconds) {
      failure(username, WRONG_PASSWORD, "10.0.3." + attempt++);
      var lockedUntil = appUserRepository.findByUsername(username).orElseThrow().getLockedUntil();
      assertThat(Duration.between(clock.instant(), lockedUntil))
          .isEqualTo(Duration.ofSeconds(seconds));
      clock.advance(Duration.ofSeconds(seconds));
    }
  }

  @Test
  void addressIsThrottledAfterRepeatedFailuresAcrossManyUsernamesThenRecovers() throws Exception {
    var username = account("ip-victim");
    var ip = "10.0.4.1";
    for (int i = 0; i < IP_MAX_FAILURES; i++) {
      failure("stranger-" + i, WRONG_PASSWORD, ip);
    }

    mockMvc
        .perform(signIn(username, PASSWORD, ip))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "900"));
    mockMvc.perform(signIn(username, PASSWORD, "10.0.4.2")).andExpect(status().isOk());

    clock.advance(Duration.ofMinutes(15));
    mockMvc.perform(signIn(username, PASSWORD, ip)).andExpect(status().isOk());
  }

  @Test
  void aForwardedForHeaderDoesNotEvadeTheThrottleWhenNoProxyStrategyIsConfigured()
      throws Exception {
    var ip = "10.0.5.1";
    for (int i = 0; i < IP_MAX_FAILURES; i++) {
      failure("spoofer-" + i, WRONG_PASSWORD, ip);
    }

    mockMvc
        .perform(signIn("spoofer-x", WRONG_PASSWORD, ip).header("X-Forwarded-For", "198.51.100.9"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void triggeringADelayIsAuditedWithoutThePassword(CapturedOutput output) throws Exception {
    var username = account("audited");
    for (int i = 0; i < THRESHOLD; i++) {
      failure(username, WRONG_PASSWORD, "10.0.6.1");
    }

    assertThat(output.getAll())
        .contains("Sign-in delay applied: username=" + username)
        .doesNotContain(WRONG_PASSWORD)
        .doesNotContain(PASSWORD);
  }

  @Test
  void aRequestSuppliedUsernameCannotForgeLogLines(CapturedOutput output) throws Exception {
    // JSON escapes decode to a real CR LF inside the username.
    failure("mallory\\r\\nWARN forged entry", WRONG_PASSWORD, "10.0.8.1");

    assertThat(output.getAll())
        .contains("username=mallory\\r\\nWARN forged entry")
        .doesNotContain("mallory\r\n")
        .doesNotContain("mallory\n");
  }

  @Test
  void anOverlongRequestSuppliedUsernameIsTruncatedInTheLog(CapturedOutput output)
      throws Exception {
    failure("x".repeat(500), WRONG_PASSWORD, "10.0.8.2");

    assertThat(output.getAll()).doesNotContain("x".repeat(200));
  }

  private String account(String username) {
    appUserRepository.save(
        AppUser.builder()
            .username(username)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .build());
    return username;
  }

  private String failure(String username, String password, String ip) throws Exception {
    return mockMvc
        .perform(signIn(username, password, ip))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static MockHttpServletRequestBuilder signIn(String username, String password, String ip) {
    return post("/login")
        .with(
            request -> {
              request.setRemoteAddr(ip);
              return request;
            })
        .cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN))
        .header(CSRF_HEADER, CSRF_TOKEN)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
  }
}
