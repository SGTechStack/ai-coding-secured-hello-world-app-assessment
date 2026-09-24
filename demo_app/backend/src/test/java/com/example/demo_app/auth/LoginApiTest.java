package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HTTP seam for {@code /api/v1/auth}: real security filter chain, real H2 database seeded by
 * Flyway (the {@code test} profile adds the demo-user seed). State-changing requests use the real
 * SPA CSRF flow ({@link SpaAuthFlow}): GET the {@code XSRF-TOKEN} cookie, echo it in {@code
 * X-XSRF-TOKEN}.
 *
 * <p>Do not use spring-security-test's {@code csrf()} post-processor here: it permanently swaps
 * the shared {@code CsrfFilter}'s repository for a session-based one, breaking cookie assertions
 * in later tests that share the cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginApiTest {

  private static final String VALID_LOGIN = SpaAuthFlow.DEMO_LOGIN;

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void validCredentialsReturnProfileAndEstablishAuthenticatedSession() throws Exception {
    MvcResult result =
        mvc.perform(loginRequest())
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.username").value("johndoe"))
            .andExpect(jsonPath("$.firstName").value("John"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();

    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    SecurityContext context = securityContext(session);
    assertThat(context.getAuthentication().isAuthenticated()).isTrue();
    assertThat(context.getAuthentication().getName()).isEqualTo("johndoe");
  }

  @Test
  void loginRotatesAnExistingSessionId() throws Exception {
    MockHttpSession preLoginSession = new MockHttpSession();
    String preLoginId = preLoginSession.getId();

    MvcResult result =
        mvc.perform(loginRequest().session(preLoginSession))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(preLoginId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"username\": \"\", \"password\": \"Password123!\"}",
        "{\"username\": \"johndoe\", \"password\": \"   \"}",
        "{\"username\": \"\", \"password\": \"\"}",
        "{\"password\": \"Password123!\"}",
        "{\"username\": \"johndoe\"}",
        "{}"
      })
  void blankOrMissingFieldIsRejectedWith400JsonAndNoSession(String body) throws Exception {
    MvcResult result =
        mvc.perform(loginRequest().content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpectAll(
                apiError(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Username and password are required",
                    "/api/v1/auth/login"))
            .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void unknownUsernameAndWrongPasswordGetIdenticalGeneric401() throws Exception {
    JsonNode unknownUser =
        invalidCredentials(
            """
            {"username": "nobody", "password": "Password123!"}
            """);
    JsonNode wrongPassword =
        invalidCredentials(
            """
            {"username": "johndoe", "password": "wrong-password"}
            """);

    // Only the timestamp may differ between the two bodies.
    assertThat(withoutTimestamp(wrongPassword)).isEqualTo(withoutTimestamp(unknownUser));
  }

  @Test
  void failedLoginDoesNotAuthenticateTheSession() throws Exception {
    MockHttpSession session = new MockHttpSession();

    mvc.perform(
            loginRequest(
                    """
                    {"username": "johndoe", "password": "wrong-password"}
                    """)
                .session(session))
        .andExpect(status().isUnauthorized());

    assertThat(securityContext(session)).isNull();
  }

  @Test
  void loginWithoutCsrfTokenIsRejectedWithJson() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LOGIN))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpectAll(
            apiError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", "/api/v1/auth/login"))
        .andExpect(cookie().doesNotExist("JSESSIONID"));
  }

  @Test
  void loginWithMismatchedCsrfTokenIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                .header("X-XSRF-TOKEN", "different-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LOGIN))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrfEndpointIssuesJsReadableTokenCookie() throws Exception {
    Cookie xsrf =
        mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");

    assertThat(xsrf).isNotNull();
    assertThat(xsrf.getValue()).isNotBlank();
    assertThat(xsrf.isHttpOnly()).isFalse();
  }

  @Test
  void unauthenticatedApiRequestGets401JsonNotRedirect() throws Exception {
    mvc.perform(get("/api/v1/anything-protected").accept(MediaType.TEXT_HTML))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Location"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpectAll(
            apiError(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Unauthorized",
                "/api/v1/anything-protected"));
  }

  @Test
  void meReturnsProfileForTheSessionEstablishedByLogin() throws Exception {
    MockHttpSession session =
        (MockHttpSession) mvc.perform(loginRequest()).andReturn().getRequest().getSession(false);

    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.username").value("johndoe"))
        .andExpect(jsonPath("$.firstName").value("John"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void meWithoutSessionGets401JsonAndACsrfToken() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/v1/auth/me").accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpectAll(
                apiError(
                    HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", "/api/v1/auth/me"))
            .andReturn();

    // Like every GET under /api/v1/auth, /me primes the SPA's CSRF cookie.
    assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
  }

  @Test
  void meWithAnUnknownSessionGets401() throws Exception {
    mvc.perform(get("/api/v1/auth/me").session(new MockHttpSession()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Unauthorized"));
  }

  @Test
  void loginRejectsANonJsonBody() throws Exception {
    mvc.perform(loginRequest().contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isUnsupportedMediaType());
  }

  /** Performs a login that must fail with the generic 401; returns the parsed body. */
  private JsonNode invalidCredentials(String body) throws Exception {
    String response =
        mvc.perform(loginRequest(body))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpectAll(
                apiError(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Invalid username or password",
                    "/api/v1/auth/login"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response);
  }

  private static JsonNode withoutTimestamp(JsonNode body) {
    ObjectNode copy = body.deepCopy();
    copy.remove("timestamp");
    return copy;
  }

  /** The {@code ApiError} body shape shared by every error response. */
  private static ResultMatcher[] apiError(
      HttpStatus status, String code, String message, String path) {
    return new ResultMatcher[] {
      jsonPath("$.status").value(status.value()),
      jsonPath("$.code").value(code),
      jsonPath("$.message").value(message),
      jsonPath("$.timestamp").isString(),
      jsonPath("$.path").value(path)
    };
  }

  /** The security context saved in {@code session} by login, or {@code null} if none was. */
  private static SecurityContext securityContext(HttpSession session) {
    return (SecurityContext)
        session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  }

  /** A login POST carrying a freshly issued CSRF cookie and matching header, as the SPA sends. */
  private MockHttpServletRequestBuilder loginRequest() throws Exception {
    return loginRequest(VALID_LOGIN);
  }

  private MockHttpServletRequestBuilder loginRequest(String body) throws Exception {
    return SpaAuthFlow.loginRequest(mvc, body);
  }
}
