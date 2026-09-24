package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.credentialsJson;
import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.registerRequest;
import static com.example.demo_app.auth.SpaAuthFlow.registrationJson;
import static com.example.demo_app.auth.SpaAuthFlow.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Self-registration at the HTTP seam. Every test registers its own uniquely named users (the
 * in-memory database is shared by all test classes) and sends from its own client address (the
 * registration throttle's counters outlive a test in the cached context).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RegistrationApiTest {

  private static final String PASSWORD = "correct horse battery";
  private static final String VALIDATION_FAILED_MESSAGE = "Some fields are invalid";
  private static final String[] ALL_FIELDS = {"email", "firstName", "password", "username"};

  @Autowired private MockMvc mvc;

  @Test
  void registersAUserWith201AndTheProfileButNoSession() throws Exception {
    String name = uniqueName();

    MvcResult result =
        register(registrationJson(name.toUpperCase(), name + "@Example.COM", "Ada", PASSWORD))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.username").value(name))
            .andExpect(jsonPath("$.firstName").value("Ada"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(cookie().doesNotExist("JSESSIONID"))
            .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void theNewUserCanLogInInAnyCase() throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());

    MockHttpSession session = logIn(mvc, name.toUpperCase(), PASSWORD);

    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(name))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  void roleAndEnabledInTheBodyHaveNoEffect() throws Exception {
    String name = uniqueName();
    String body =
        """
        {"username": "%s", "email": "%s@example.com", "firstName": "Eve",
         "password": "%s", "role": "ADMIN", "enabled": false}
        """
            .formatted(name, name, PASSWORD);

    register(body).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));

    // Enabled (it can log in) and a plain USER in its session too.
    MockHttpSession session = logIn(mvc, name, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  void aTakenUsernameInAnyCaseIsA409NamingTheUsernameAndCreatesNothing() throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());

    register(registrationJson(name.toUpperCase(), name + "-2@example.com", "Bob", "another pass 1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
        .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("This username is already taken."));

    // The refused request created nothing: its email is still free and its password never set.
    mvc.perform(loginRequest(mvc, credentialsJson(name, "another pass 1")).with(fromIp(uniqueIp())))
        .andExpect(status().isUnauthorized());
    register(registrationJson(uniqueName(), name + "-2@example.com", "Bob", PASSWORD))
        .andExpect(status().isCreated());
  }

  @Test
  void aTakenEmailInAnyCaseIsA409NamingTheEmail() throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());

    String sameEmail = "  " + name.toUpperCase() + "@EXAMPLE.com ";
    register(registrationJson(uniqueName(), sameEmail, "Bob", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"))
        .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("An account with this email already exists."));
  }

  @Test
  void aTakenUsernameAndEmailAreBothNamed() throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());

    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fieldErrors[*].field", contains("username", "email")));
  }

  /** Too short, on the common list (in any case), over 72 UTF-8 bytes, over 64 characters. */
  static List<String> refusedPasswords() {
    return List.of("only11chars", "UNBELIEVABLE", "é".repeat(36) + "!", "x".repeat(65));
  }

  @ParameterizedTest
  @MethodSource("refusedPasswords")
  void aPasswordThePolicyRefusesIsA400NamingThePassword(String password, CapturedOutput output)
      throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "Ada", password))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(VALIDATION_FAILED_MESSAGE))
        .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
        .andExpect(content().string(not(containsString(password))));

    assertThat(output.getAll()).doesNotContain(password);
    // Nothing was created.
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());
  }

  @Test
  void everyInvalidFieldIsNamedInOne400() throws Exception {
    register(registrationJson("x!", "not-an-email", "  ", "short"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(VALIDATION_FAILED_MESSAGE))
        .andExpect(jsonPath("$.fieldErrors[*].field", contains(ALL_FIELDS)))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("Enter a valid email address."))
        .andExpect(
            jsonPath("$.fieldErrors[1].message")
                .value("First name must be 1 to 100 characters, without control characters."))
        .andExpect(
            jsonPath("$.fieldErrors[2].message").value("Password must be 12 to 64 characters."))
        .andExpect(
            jsonPath("$.fieldErrors[3].message")
                .value("Username must be 3 to 50 letters, digits, dots, hyphens or underscores."));
  }

  @Test
  void missingFieldsAreNamed() throws Exception {
    register("{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", contains(ALL_FIELDS)));
  }

  @Test
  void usernameRulesAndLimits() throws Exception {
    String tooLong = "u".repeat(51);
    for (String username : List.of("ab", tooLong, "has space", "emoji😀", "semi;colon")) {
      register(registrationJson(username, uniqueName() + "@example.com", "Ada", PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors[*].field", contains("username")));
    }
  }

  @Test
  void firstNameWithAControlCharacterOrOver100CharactersIsRefused() throws Exception {
    for (String firstName : List.of("Ada\r\nLovelace", "Ada\u0000", "a".repeat(101))) {
      String name = uniqueName();
      register(registrationJson(name, name + "@example.com", firstName, PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors[*].field", contains("firstName")));
    }
  }

  @Test
  void anOverlongEmailIsRefused() throws Exception {
    String domain = String.join(".", "b".repeat(63), "c".repeat(63), "d".repeat(58), "com");
    String email = "a".repeat(64) + "@" + domain;
    assertThat(email).hasSize(255);
    register(registrationJson(uniqueName(), email, "Ada", PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", contains("email")));
  }

  @Test
  void htmlInTheFirstNameIsStoredAndReturnedVerbatimAsJson() throws Exception {
    String name = uniqueName();
    String firstName = "<img src=x onerror=alert(1)>\"'&";

    MvcResult created =
        register(registrationJson(name, name + "@example.com", firstName, PASSWORD))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.firstName").value(firstName))
            .andReturn();
    assertThat(created.getResponse().getContentAsString()).contains("<img src=x onerror=alert(1)>");

    MockHttpSession session = logIn(mvc, name, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.firstName").value(firstName));
  }

  @Test
  void theFirstNameIsTrimmed() throws Exception {
    String name = uniqueName();
    register(registrationJson(name, name + "@example.com", "  Ada  ", PASSWORD))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.firstName").value("Ada"));
  }

  @Test
  void registrationIsAuditedAndThePasswordIsNeverLogged(CapturedOutput output) throws Exception {
    String name = uniqueName();
    String ip = uniqueIp();
    // Unique to this test, so no other test's output can contain it.
    String password = "audit " + UUID.randomUUID();

    mvc.perform(
            registerRequest(mvc, registrationJson(name, name + "@example.com", "Ada", password))
                .with(fromIp(ip)))
        .andExpect(status().isCreated());

    assertThat(auditLines(output))
        .singleElement()
        .asString()
        .contains("event=USER_REGISTERED actor=" + name + " ip=" + ip + " outcome=success");
    assertThat(output.getAll()).doesNotContain(password);
  }

  @Test
  void registrationNeedsTheCsrfToken() throws Exception {
    String name = uniqueName();
    mvc.perform(
            post("/api/v1/auth/register")
                .with(fromIp(uniqueIp()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(name, name + "@example.com", "Ada", PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());
  }

  @Test
  void theEleventhRequestFromOneIpInTheWindowGets429(CapturedOutput output) throws Exception {
    String ip = uniqueIp();
    // Every request counts, whatever its outcome.
    for (int i = 0; i < 10; i++) {
      mvc.perform(registerRequest(mvc, "{}").with(fromIp(ip))).andExpect(status().isBadRequest());
    }

    String name = uniqueName();
    mvc.perform(
            registerRequest(mvc, registrationJson(name, name + "@example.com", "Ada", PASSWORD))
                .with(fromIp(ip)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(jsonPath("$.message").value("Too many attempts. Please try again later."));

    assertThat(auditLines(output))
        .last()
        .asString()
        .contains(
            "event=REQUEST_THROTTLED actor=anonymous ip="
                + ip
                + " outcome=failure endpoint=register");
    // The refused request created nothing, and another address is unaffected.
    register(registrationJson(name, name + "@example.com", "Ada", PASSWORD))
        .andExpect(status().isCreated());
  }

  /** A registration POST from a fresh client address, so it never meets the throttle. */
  private ResultActions register(String body) throws Exception {
    return mvc.perform(registerRequest(mvc, body).with(fromIp(uniqueIp())));
  }

  private static String uniqueName() {
    return "reg-" + UUID.randomUUID().toString().substring(0, 12);
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
