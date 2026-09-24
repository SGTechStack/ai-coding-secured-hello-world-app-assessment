package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Audit lines for login and logout, asserted on the captured console output at the HTTP seam.
 *
 * <p>Logback is initialised once per JVM by the first test context, so the console may be ECS JSON
 * or plain text depending on test order. The assertions therefore match the audit message ({@code
 * key=value} text, identical in both formats); {@code AuditLogTest} covers the key-value pairs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuditLogApiTest {

  private static final String PASSWORD = "Password123!";

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void successfulLoginIsAudited(CapturedOutput output) throws Exception {
    logIn(mvc);

    assertThat(auditLines(output))
        .singleElement(STRING)
        .contains("event=LOGIN_SUCCESS actor=johndoe ip=127.0.0.1 outcome=success");
  }

  @Test
  void failedLoginIsAuditedWithTheSubmittedUsername(CapturedOutput output) throws Exception {
    mvc.perform(
            loginRequest(
                mvc,
                """
                {"username": "johndoe", "password": "wrong-password"}
                """))
        .andExpect(status().isUnauthorized());

    assertThat(auditLines(output))
        .singleElement(STRING)
        .contains("event=LOGIN_FAILURE actor=johndoe ip=127.0.0.1 outcome=failure");
    assertThat(output.getAll()).doesNotContain("wrong-password");
  }

  @Test
  void blankFieldsAreNotALoginAttempt(CapturedOutput output) throws Exception {
    mvc.perform(loginRequest(mvc, "{\"username\": \"johndoe\", \"password\": \"\"}"))
        .andExpect(status().isBadRequest());

    assertThat(auditLines(output)).isEmpty();
  }

  @Test
  void logoutIsAuditedWithTheSessionUser(CapturedOutput output) throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(withCsrf(mvc, post("/api/v1/auth/logout")).session(session))
        .andExpect(status().isNoContent());

    List<String> lines = auditLines(output);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(1)).contains("event=LOGOUT actor=johndoe ip=127.0.0.1 outcome=success");
  }

  @Test
  void logoutWithoutASessionIsAuditedAsAnonymous(CapturedOutput output) throws Exception {
    mvc.perform(withCsrf(mvc, post("/api/v1/auth/logout"))).andExpect(status().isNoContent());

    assertThat(auditLines(output))
        .singleElement(STRING)
        .contains("event=LOGOUT actor=anonymous ip=127.0.0.1 outcome=success");
  }

  @Test
  void controlCharactersInTheUsernameCannotForgeALine(CapturedOutput output) throws Exception {
    String forged = "eve\r\nevent=LOGIN_SUCCESS actor=admin\u0000\t x";

    mvc.perform(
            loginRequest(
                mvc, objectMapper.writeValueAsString(new LoginRequest(forged, "wrong-password"))))
        .andExpect(status().isUnauthorized());

    assertThat(auditLines(output))
        .singleElement(STRING)
        .contains("event=LOGIN_FAILURE actor=eve__event=LOGIN_SUCCESS actor=admin___x ip=");
    assertThat(output.getAll()).doesNotContain("\nevent=LOGIN_SUCCESS").doesNotContain(" ");
  }

  @Test
  void passwordsAndCsrfTokensNeverAppear(CapturedOutput output) throws Exception {
    Cookie loginXsrf = SpaAuthFlow.freshCsrfCookie(mvc);
    MockHttpSession session =
        (MockHttpSession)
            mvc.perform(
                    withToken(post("/api/v1/auth/login"), loginXsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SpaAuthFlow.DEMO_LOGIN))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    Cookie logoutXsrf = SpaAuthFlow.freshCsrfCookie(mvc);
    mvc.perform(withToken(post("/api/v1/auth/logout"), logoutXsrf).session(session))
        .andExpect(status().isNoContent());

    // MockMvc session IDs are short counters, so they can't be searched for; AuditLogTest pins the
    // audit line to its fixed fields, none of which is a session ID.
    assertThat(auditLines(output)).hasSize(2);
    assertThat(output.getAll())
        .doesNotContain(PASSWORD)
        .doesNotContain(loginXsrf.getValue())
        .doesNotContain(logoutXsrf.getValue())
        .doesNotContain("JSESSIONID");
  }

  private static MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, Cookie xsrf) {
    return request.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
  }

  /** The {@code AUDIT} logger's console lines in {@code output}, in order, in either format. */
  private static List<String> auditLines(CapturedOutput output) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.contains("\"logger\":\"AUDIT\"") || line.contains(" AUDIT "))
        .toList();
  }
}
