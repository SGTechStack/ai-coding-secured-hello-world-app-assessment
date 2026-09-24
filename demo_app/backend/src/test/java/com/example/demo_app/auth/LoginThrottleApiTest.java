package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.DEMO_LOGIN;
import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.MutableClock;
import com.example.demo_app.TestClockConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Per-IP login throttling at the HTTP seam, with the default limit of 20 failed logins per IP per
 * 15 minutes. Each test uses its own client addresses (the counters outlive a test in the cached
 * context) and the controllable clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class LoginThrottleApiTest {

  private static final int LIMIT = 20;
  private static final String THROTTLED_MESSAGE = "Too many attempts. Please try again later.";

  @Autowired private MockMvc mvc;

  @Autowired private MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(TestClockConfig.START);
  }

  @Test
  void twentyFailuresAcrossUsernamesThrottleTheIpEvenWithCorrectCredentials(CapturedOutput output)
      throws Exception {
    String ip = "198.51.100.20";
    sprayFailures(ip, LIMIT);

    login(ip, DEMO_LOGIN)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(jsonPath("$.message").value(THROTTLED_MESSAGE))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

    List<String> audit = auditLines(output);
    assertThat(audit).hasSize(LIMIT + 1);
    assertThat(audit.getLast())
        .contains("event=LOGIN_THROTTLED actor=johndoe ip=" + ip + " outcome=failure");
  }

  @Test
  void theTargetedAccountIsNotLockedAndOtherIpsAreUnaffected() throws Exception {
    String attacker = "198.51.100.21";
    sprayFailures(attacker, LIMIT);
    login(attacker, DEMO_LOGIN).andExpect(status().isTooManyRequests());

    login("198.51.100.22", DEMO_LOGIN).andExpect(status().isOk());
  }

  @Test
  void retryAfterCountsDownAndTheWindowResetsWhenTheClockMovesPastIt() throws Exception {
    String ip = "198.51.100.23";
    sprayFailures(ip, LIMIT);

    clock.advance(Duration.ofMinutes(10));
    login(ip, DEMO_LOGIN)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"));

    clock.advance(Duration.ofMinutes(5));
    login(ip, DEMO_LOGIN).andExpect(status().isOk());
  }

  @Test
  void onlyFailuresCount() throws Exception {
    String ip = "198.51.100.24";
    sprayFailures(ip, LIMIT - 1);
    login(ip, DEMO_LOGIN).andExpect(status().isOk());
    login(ip, DEMO_LOGIN).andExpect(status().isOk());

    login(ip, wrongPassword("johndoe")).andExpect(status().isUnauthorized());
    login(ip, DEMO_LOGIN).andExpect(status().isTooManyRequests());
  }

  @Test
  void aThrottledCorsRequestExposesRetryAfterToTheSpa() throws Exception {
    String ip = "198.51.100.25";
    sprayFailures(ip, LIMIT);

    mvc.perform(
            loginRequest(mvc, DEMO_LOGIN)
                .with(fromIp(ip))
                .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"));
  }

  /** {@code count} wrong-password logins from {@code ip}, each for a different username. */
  private void sprayFailures(String ip, int count) throws Exception {
    for (int i = 0; i < count; i++) {
      login(ip, wrongPassword("spray-" + i)).andExpect(status().isUnauthorized());
    }
  }

  private ResultActions login(String ip, String body) throws Exception {
    return mvc.perform(loginRequest(mvc, body).with(fromIp(ip)));
  }

  private static String wrongPassword(String username) {
    return "{\"username\": \"" + username + "\", \"password\": \"wrong-password\"}";
  }

  /** The {@code AUDIT} logger's console lines in {@code output}, in either format. */
  private static List<String> auditLines(CapturedOutput output) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.contains("\"logger\":\"AUDIT\"") || line.contains(" AUDIT "))
        .toList();
  }
}
