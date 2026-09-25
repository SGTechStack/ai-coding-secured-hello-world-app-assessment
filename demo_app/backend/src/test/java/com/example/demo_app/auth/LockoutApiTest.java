package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.credentialsJson;
import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.register;
import static com.example.demo_app.auth.SpaAuthFlow.setEnabled;
import static com.example.demo_app.auth.SpaAuthFlow.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.MutableClock;
import com.example.demo_app.TestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Account lockout at the HTTP seam, with the defaults of 5 consecutive failures and a 15-minute
 * lock. Each test registers its own user and sends from its own client address (so its failures
 * never touch the shared IP throttle), and moves the controllable clock instead of sleeping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class LockoutApiTest {

  private static final String PASSWORD = "lockout test passphrase";
  private static final AtomicInteger NEXT_USER = new AtomicInteger();
  private static final Pattern EVENT = Pattern.compile("event=(\\w+)");

  @Autowired private MockMvc mvc;

  @Autowired private MutableClock clock;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AuthenticationManager authenticationManager;

  private String username;
  private String ip;

  @BeforeEach
  void freshUserAndClock() throws Exception {
    clock.set(TestClockConfig.START);
    username = "lockout-" + NEXT_USER.incrementAndGet() + "-" + System.nanoTime();
    ip = uniqueIp();
    register(mvc, username, PASSWORD);
  }

  @Test
  void fourFailuresDoNotLock() throws Exception {
    fail(4);

    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void theFifthFailureLocksAndTheCorrectPasswordThenGetsTheIdentical401(CapturedOutput output)
      throws Exception {
    fail(5);

    JsonNode locked = unauthorized(login(PASSWORD));
    JsonNode wrongPassword = unauthorized(login("wrong password"));
    JsonNode unknownUser = unauthorized(login("nobody-" + username, PASSWORD));

    assertThat(locked).isEqualTo(wrongPassword).isEqualTo(unknownUser);
    assertThat(locked.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(locked.get("message").asText()).isEqualTo("Invalid username or password");

    List<String> audit = auditLines(output);
    assertThat(audit.stream().map(LockoutApiTest::event))
        .containsExactly(
            "USER_REGISTERED",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE",
            "ACCOUNT_LOCKED",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE",
            "LOGIN_FAILURE");
    assertThat(audit.get(6))
        .contains("event=ACCOUNT_LOCKED actor=" + username + " ip=" + ip + " outcome=failure");
  }

  @Test
  void afterTheCooldownTheCorrectPasswordSucceedsAndTheCounterIsReset() throws Exception {
    fail(5);
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));

    login(PASSWORD).andExpect(status().isOk());

    fail(4);
    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void theLockEndsExactlyFifteenMinutesAfterTheFifthFailure() throws Exception {
    fail(5);

    clock.advance(Duration.ofMinutes(15).minusSeconds(1));
    login(PASSWORD).andExpect(status().isUnauthorized());

    clock.advance(Duration.ofSeconds(1));
    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void failuresWhileLockedNeitherExtendTheLockNorCount() throws Exception {
    fail(5);

    clock.advance(Duration.ofMinutes(14));
    fail(10);

    clock.advance(Duration.ofMinutes(1));
    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void anExpiredLockStartsAFreshCount() throws Exception {
    fail(5);
    clock.advance(Duration.ofMinutes(16));

    fail(4);
    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void aSuccessfulLoginResetsTheCounter() throws Exception {
    fail(4);
    login(PASSWORD).andExpect(status().isOk());

    fail(4);
    login(PASSWORD).andExpect(status().isOk());
  }

  @Test
  void usernameCaseDoesNotDodgeTheCounter() throws Exception {
    for (int i = 0; i < 5; i++) {
      login(i % 2 == 0 ? username.toUpperCase() : username, "wrong password")
          .andExpect(status().isUnauthorized());
    }

    login(PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void aDisabledAccountWithTheCorrectPasswordIsToldItIsDisabled() throws Exception {
    setEnabled(mvc, username, false);

    JsonNode disabled = unauthorized(login(PASSWORD));

    assertThat(disabled.get("code").asText()).isEqualTo("ACCOUNT_DISABLED");
    assertThat(disabled.get("message").asText())
        .isEqualTo("Your account has been disabled. Please contact an admin.");
  }

  @Test
  void aDisabledAccountWithAWrongPasswordGetsTheGeneric401() throws Exception {
    JsonNode enabledWrongPassword = unauthorized(login("wrong password"));
    setEnabled(mvc, username, false);

    JsonNode disabledWrongPassword = unauthorized(login("wrong password"));

    assertThat(disabledWrongPassword).isEqualTo(enabledWrongPassword);
    assertThat(disabledWrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
  }

  /**
   * The password is compared before the lock and enabled checks: a wrong password is reported as
   * bad credentials whatever the account's state, so every path pays for the BCrypt comparison.
   */
  @Test
  void thePasswordIsCheckedBeforeTheLockAndEnabledFlags() throws Exception {
    fail(5);
    assertThatThrownBy(() -> authenticate("wrong password"))
        .isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> authenticate(PASSWORD)).isInstanceOf(LockedException.class);

    clock.advance(Duration.ofMinutes(16));
    setEnabled(mvc, username, false);
    assertThatThrownBy(() -> authenticate("wrong password"))
        .isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> authenticate(PASSWORD)).isInstanceOf(DisabledException.class);
  }

  private void authenticate(String password) {
    authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(username, password));
  }

  /** {@code count} wrong-password logins for this test's user. */
  private void fail(int count) throws Exception {
    for (int i = 0; i < count; i++) {
      login("wrong password").andExpect(status().isUnauthorized());
    }
  }

  private ResultActions login(String password) throws Exception {
    return login(username, password);
  }

  private ResultActions login(String user, String password) throws Exception {
    return mvc.perform(loginRequest(mvc, credentialsJson(user, password)).with(fromIp(ip)));
  }

  /** The {@code 401} body without its timestamp, which is the only part allowed to differ. */
  private JsonNode unauthorized(ResultActions result) throws Exception {
    String body =
        result.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
    ObjectNode json = (ObjectNode) objectMapper.readTree(body);
    json.remove("timestamp");
    return json;
  }

  private static String event(String auditLine) {
    Matcher matcher = EVENT.matcher(auditLine);
    assertThat(matcher.find()).as(auditLine).isTrue();
    return matcher.group(1);
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
