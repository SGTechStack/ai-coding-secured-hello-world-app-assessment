package com.example.demo_app.passwordreset;

import static com.example.demo_app.auth.SpaAuthFlow.credentialsJson;
import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.register;
import static com.example.demo_app.auth.SpaAuthFlow.uniqueIp;
import static com.example.demo_app.auth.SpaAuthFlow.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.MutableClock;
import com.example.demo_app.TestClockConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Password reset at the HTTP seam. The emailed link is read from the stub email service's log line
 * (captured output), which is also how the dev-profile e2e suite reads it. Every test registers its
 * own users and sends from its own client address; the clock is reset before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetApiTest {

  private static final String OLD_PASSWORD = "correct horse battery";
  private static final String NEW_PASSWORD = "stapled paper clip";
  private static final String INVALID_TOKEN = "This reset link is invalid or has expired.";
  private static final Pattern LINK =
      Pattern.compile("http://localhost:3000/reset-password#token=([A-Za-z0-9_-]{43})");
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mvc;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbc;

  /** This test's console output, where the stub email service logs each link. */
  private CapturedOutput output;

  @BeforeEach
  void setUp(CapturedOutput output) {
    this.output = output;
    clock.set(TestClockConfig.START);
  }

  @Test
  void theRequestIsAnIdentical202ForKnownUnknownDisabledAndMalformedEmails(CapturedOutput output)
      throws Exception {
    String known = newUser();
    String disabled = newUser();
    jdbc.update("UPDATE user_account SET enabled = FALSE WHERE username = ?", disabled);

    for (String body :
        List.of(
            emailJson(known.toUpperCase() + "@Example.com "),
            emailJson(uniqueName() + "@example.com"),
            emailJson(disabled + "@example.com"),
            emailJson("not an email"),
            emailJson(""),
            "{}")) {
      requestReset(body)
          .andExpect(status().isAccepted())
          .andExpect(content().string(""))
          .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE));
    }

    // Only the known, enabled account was sent a link.
    assertThat(emailLines(output)).singleElement().asString().contains(known + "@example.com");
  }

  @Test
  void theLinkSetsANewPasswordAnswering204AndTheOldPasswordStopsWorking() throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);

    confirm(token, NEW_PASSWORD)
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    logIn(mvc, name, NEW_PASSWORD);
    expectLoginFails(name, OLD_PASSWORD);
  }

  @Test
  void aUsedLinkIsRejected() throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);
    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

    expectInvalidToken(confirm(token, "yet another passphrase"));
    logIn(mvc, name, NEW_PASSWORD);
  }

  @Test
  void aLinkPastItsThirtyMinutesIsRejectedAndThePasswordIsUnchanged() throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);

    clock.advance(Duration.ofMinutes(30));
    expectInvalidToken(confirm(token, NEW_PASSWORD));

    logIn(mvc, name, OLD_PASSWORD);
    expectLoginFails(name, NEW_PASSWORD);
  }

  @Test
  void aLinkJustInsideItsThirtyMinutesWorks() throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);

    clock.advance(Duration.ofMinutes(30).minusSeconds(1));
    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void anOlderLinkStopsWorkingOnceANewerOneIsRequested() throws Exception {
    String name = newUser();
    String older = requestTokenFor(name);
    String newer = requestTokenFor(name);
    assertThat(newer).isNotEqualTo(older);

    expectInvalidToken(confirm(older, NEW_PASSWORD));
    logIn(mvc, name, OLD_PASSWORD);

    confirm(newer, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void anUnknownOrMissingTokenIsRejected() throws Exception {
    expectInvalidToken(confirm("not-a-token", NEW_PASSWORD));
    expectInvalidToken(
        mvc.perform(
            withCsrf(mvc, post("/api/v1/auth/password-reset/confirm"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", NEW_PASSWORD)))));
  }

  @Test
  void aPasswordThePolicyRefusesIs400AndLeavesTheLinkUsable(CapturedOutput output)
      throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);

    for (String refused : List.of("short", "Unbelievable")) {
      confirm(token, refused)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
          .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    assertThat(auditLines(output)).noneMatch(line -> line.contains("PASSWORD_RESET_REJECTED"));
  }

  @Test
  void theResetEndsEverySessionOfThatUserAndOnlyThatUser() throws Exception {
    String name = newUser();
    String other = newUser();
    MockHttpSession first = logIn(mvc, name, OLD_PASSWORD);
    MockHttpSession second = logIn(mvc, name, OLD_PASSWORD);
    MockHttpSession bystander = logIn(mvc, other, OLD_PASSWORD);

    confirm(requestTokenFor(name), NEW_PASSWORD).andExpect(status().isNoContent());

    for (MockHttpSession session : List.of(first, second)) {
      mvc.perform(get("/api/v1/auth/me").session(session))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
          .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }
    mvc.perform(get("/api/v1/auth/me").session(bystander)).andExpect(status().isOk());
    // A new login works as usual.
    MockHttpSession fresh = logIn(mvc, name, NEW_PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(fresh)).andExpect(status().isOk());
  }

  @Test
  void theResetClearsTheLockout() throws Exception {
    String name = newUser();
    String ip = uniqueIp();
    for (int i = 0; i < 5; i++) {
      mvc.perform(loginRequest(mvc, credentialsJson(name, "wrong passphrase")).with(fromIp(ip)))
          .andExpect(status().isUnauthorized());
    }
    // Locked: even the right password is refused.
    expectLoginFails(name, OLD_PASSWORD);

    confirm(requestTokenFor(name), NEW_PASSWORD).andExpect(status().isNoContent());

    // No waiting out the 15 minutes, and the count starts from zero again.
    logIn(mvc, name, NEW_PASSWORD);
    for (int i = 0; i < 4; i++) {
      expectLoginFails(name, "wrong passphrase");
    }
    logIn(mvc, name, NEW_PASSWORD);
  }

  @Test
  void onlyTheTokensHashIsStored() throws Exception {
    String name = newUser();
    String token = requestTokenFor(name);

    List<String> hashes =
        jdbc.queryForList(
            "SELECT t.token_hash FROM password_reset_token t"
                + " JOIN user_account u ON u.id = t.user_id WHERE u.username = ?",
            String.class,
            name);
    assertThat(hashes).containsExactly(PasswordReset.sha256(token)).doesNotContain(token);
  }

  @Test
  void everyStepIsAuditedWithoutTheEmailOrToken(CapturedOutput output) throws Exception {
    String name = newUser();
    String unknownEmail = uniqueName() + "@example.com";
    String ip = uniqueIp();

    mvc.perform(resetRequest(emailJson(name + "@example.com")).with(fromIp(ip)))
        .andExpect(status().isAccepted());
    mvc.perform(resetRequest(emailJson(unknownEmail)).with(fromIp(ip)))
        .andExpect(status().isAccepted());
    String token = latestToken(output, name);
    mvc.perform(confirmRequest(token, NEW_PASSWORD).with(fromIp(ip)))
        .andExpect(status().isNoContent());
    mvc.perform(confirmRequest(token, NEW_PASSWORD).with(fromIp(ip)))
        .andExpect(status().isBadRequest());
    mvc.perform(confirmRequest("bogus", NEW_PASSWORD).with(fromIp(ip)))
        .andExpect(status().isBadRequest());

    List<String> audit = auditLines(output);
    assertThat(audit)
        .anyMatch(
            line ->
                line.contains(
                    "event=PASSWORD_RESET_REQUESTED actor=anonymous ip="
                        + ip
                        + " outcome=success target="
                        + name))
        .anyMatch(
            line ->
                line.contains(
                    "event=PASSWORD_RESET_REQUESTED actor=anonymous ip="
                        + ip
                        + " outcome=success target=unknown"))
        .anyMatch(
            line ->
                line.contains(
                    "event=PASSWORD_RESET_COMPLETED actor=" + name + " ip=" + ip + " outcome=success"))
        .anyMatch(
            line ->
                line.contains(
                    "event=PASSWORD_RESET_REJECTED actor=anonymous ip="
                        + ip
                        + " outcome=failure target="
                        + name))
        .anyMatch(
            line ->
                line.contains(
                    "event=PASSWORD_RESET_REJECTED actor=anonymous ip="
                        + ip
                        + " outcome=failure target=unknown"));
    assertThat(audit).noneMatch(line -> line.contains(unknownEmail) || line.contains(token));
    // The stub email line is the only place the token appears; the unknown email never does.
    assertThat(output.getAll()).doesNotContain(unknownEmail);
    assertThat(output.getAll().lines().filter(line -> line.contains(token)))
        .singleElement()
        .asString()
        .contains("Password reset email to " + name + "@example.com");
  }

  @Test
  void the11thRequestFromOneIpInTheWindowGets429AndIsAudited(CapturedOutput output)
      throws Exception {
    String ip = uniqueIp();
    for (int i = 0; i < 10; i++) {
      mvc.perform(resetRequest(emailJson("nobody@example.com")).with(fromIp(ip)))
          .andExpect(status().isAccepted());
    }

    mvc.perform(resetRequest(emailJson("nobody@example.com")).with(fromIp(ip)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    assertThat(auditLines(output))
        .last()
        .asString()
        .contains(
            "event=REQUEST_THROTTLED actor=anonymous ip="
                + ip
                + " outcome=failure endpoint=password-reset-request");

    mvc.perform(resetRequest(emailJson("nobody@example.com")).with(fromIp(uniqueIp())))
        .andExpect(status().isAccepted());
    clock.advance(Duration.ofMinutes(15));
    mvc.perform(resetRequest(emailJson("nobody@example.com")).with(fromIp(ip)))
        .andExpect(status().isAccepted());
  }

  @Test
  void bothEndpointsNeedTheCsrfToken() throws Exception {
    mvc.perform(
            post("/api/v1/auth/password-reset/request")
                .with(fromIp(uniqueIp()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailJson("nobody@example.com")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", "x", "newPassword", NEW_PASSWORD))))
        .andExpect(status().isForbidden());
  }

  // --- helpers ---

  /** Registers a fresh user with {@link #OLD_PASSWORD}; returns its username. */
  private String newUser() throws Exception {
    String name = uniqueName();
    register(mvc, name, OLD_PASSWORD);
    return name;
  }

  /** Requests a reset for {@code username}'s email and returns the token from the emailed link. */
  private String requestTokenFor(String username) throws Exception {
    requestReset(emailJson(username + "@example.com")).andExpect(status().isAccepted());
    return latestToken(output, username);
  }

  private ResultActions requestReset(String body) throws Exception {
    return mvc.perform(resetRequest(body).with(fromIp(uniqueIp())));
  }

  private MockHttpServletRequestBuilder resetRequest(
      String body) throws Exception {
    return withCsrf(mvc, post("/api/v1/auth/password-reset/request"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private MockHttpServletRequestBuilder confirmRequest(
      String token, String newPassword) throws Exception {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("token", token);
    body.put("newPassword", newPassword);
    return withCsrf(mvc, post("/api/v1/auth/password-reset/confirm"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(body));
  }

  private ResultActions confirm(String token, String newPassword) throws Exception {
    return mvc.perform(confirmRequest(token, newPassword));
  }

  private void expectInvalidToken(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
        .andExpect(jsonPath("$.message").value(INVALID_TOKEN))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/password-reset/confirm"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  private void expectLoginFails(String username, String password) throws Exception {
    mvc.perform(loginRequest(mvc, credentialsJson(username, password)).with(fromIp(uniqueIp())))
        .andExpect(status().isUnauthorized());
  }

  private static String latestToken(CapturedOutput output, String username) {
    String token = null;
    for (String line : emailLines(output)) {
      if (line.contains(username + "@example.com")) {
        Matcher link = LINK.matcher(line);
        assertThat(link.find()).as("reset link in %s", line).isTrue();
        token = link.group(1);
      }
    }
    assertThat(token).as("a reset link emailed to %s", username).isNotNull();
    return token;
  }

  private static List<String> emailLines(CapturedOutput output) {
    return output.getOut().lines().filter(line -> line.contains("Password reset email to")).toList();
  }

  /** The {@code AUDIT} logger's console lines in {@code output}, in either format. */
  private static List<String> auditLines(CapturedOutput output) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.contains("\"logger\":\"AUDIT\"") || line.contains(" AUDIT "))
        .toList();
  }

  private static String emailJson(String email) {
    return json(Map.of("email", email));
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static String uniqueName() {
    return "pwr-" + UUID.randomUUID().toString().substring(0, 12);
  }
}
