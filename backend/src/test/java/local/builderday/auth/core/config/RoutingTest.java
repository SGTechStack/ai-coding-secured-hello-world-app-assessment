package local.builderday.auth.core.config;

import static local.builderday.auth.core.config.RoutingTest.Caller.ADMIN;
import static local.builderday.auth.core.config.RoutingTest.Caller.USER;
import static local.builderday.auth.core.config.RoutingTest.Caller.VISITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.stream.Stream;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Path ownership (ADR 0007), observed from outside: for each path kind, caller, method and {@code Accept} header, the
 * status, the Problem Detail {@code code}, and whether the SPA document came back.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(RoutingTest.FixtureController.class)
class RoutingTest {
  private static final String HTML = "text/html";
  private static final String JSON = "application/json";
  private static final String MALFORMED = "text/html;q=not-a-number";
  /** The expected outcome: the SPA document, a non-SPA 2xx body, a status without a Problem Detail, or a code. */
  private static final String SPA = "SPA";
  private static final String OK = "OK";
  private static final String NO_CODE = "NO_CODE";

  enum Caller { VISITOR, USER, ADMIN }

  /** Endpoints that exist but have no grant in the matrix, so only the handler lookup can tell them apart. */
  @RestController
  static class FixtureController {
    @GetMapping("/api/admin/fixture") String admin() { return "admin"; }
    @GetMapping("/api/fixture/ungranted") String ungranted() { return "ungranted"; }
    // GET /api/hello is granted to USER and ADMIN; a POST handler on the same path is not.
    @PostMapping("/api/hello") String helloPost() { return "posted"; }
  }

  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;

  static Stream<Arguments> routes() {
    var rows = Stream.<Arguments>builder();
    // Frontend paths: every HTML navigation loads the app, whoever asks; the router decides what to show.
    for (Caller caller : Caller.values()) {
      for (String path : new String[] {"/", "/home", "/login", "/nope", "/some/deep/path", "/images-old.png",
          "/robots.txt", "/index.html", "/account/settings", "/error/anything"}) {
        rows.add(row(caller, GET, path, HTML, false, 200, SPA));
      }
    }
    return Stream.concat(rows.build(), Stream.of(
        row(VISITOR, GET, "/nope", null, false, 200, SPA),
        row(USER, GET, "/nope", null, false, 200, SPA),
        row(VISITOR, GET, "/home", "text/html,application/xhtml+xml,*/*;q=0.8", false, 200, SPA),
        // Frontend paths asked for something other than a page.
        row(VISITOR, GET, "/nope", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        row(USER, GET, "/home", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        row(VISITOR, GET, "/login", MALFORMED, false, 400, NO_CODE),
        row(USER, GET, "/", MALFORMED, false, 400, NO_CODE),
        row(VISITOR, POST, "/login", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(USER, POST, "/", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(USER, PUT, "/home", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(USER, PATCH, "/nope", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(ADMIN, DELETE, "/nope", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(VISITOR, POST, "/login", JSON, false, 403, "CSRF_TOKEN_REJECTED"),
        row(USER, DELETE, "/home", JSON, false, 403, "CSRF_TOKEN_REJECTED"),
        // Server-owned static paths: a file or a 404, never the app.
        row(VISITOR, GET, "/favicon.ico", null, false, 200, OK),
        row(VISITOR, GET, "/assets/missing.js", null, false, 404, "RESOURCE_NOT_FOUND"),
        row(VISITOR, GET, "/assets/missing", HTML, false, 404, "RESOURCE_NOT_FOUND"),
        row(VISITOR, GET, "/images/missing.png", HTML, false, 404, "RESOURCE_NOT_FOUND"),
        row(USER, GET, "/images/missing.png", HTML, false, 404, "RESOURCE_NOT_FOUND"),
        row(VISITOR, GET, "/csrf", null, false, 200, OK),
        // Actuator: only the load balancer's health check is open; every other endpoint stays closed.
        row(VISITOR, GET, "/actuator/health", null, false, 200, OK),
        row(VISITOR, GET, "/actuator/health", HTML, false, 406, NO_CODE),
        row(VISITOR, GET, "/actuator", HTML, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/actuator/env", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/actuator/health/db", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        // /api/**, Visitors: Authentication-required everywhere non-public, so nobody anonymous can map the API.
        row(VISITOR, GET, "/api/profile", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/api/nope", HTML, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/api", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/api/admin/fixture", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, GET, "/api/admin/nope", JSON, false, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, POST, "/api/nope", JSON, true, 401, "AUTHENTICATION_REQUIRED"),
        row(VISITOR, POST, "/api/nope", JSON, false, 403, "CSRF_TOKEN_REJECTED"),
        // /api/**, authenticated callers: missing (404), wrong method (405) or forbidden (403).
        row(USER, GET, "/api/nope", HTML, false, 404, "RESOURCE_NOT_FOUND"),
        row(ADMIN, GET, "/api/nope", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        row(USER, GET, "/api", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        row(USER, POST, "/api/nope", JSON, true, 404, "RESOURCE_NOT_FOUND"),
        row(USER, DELETE, "/api/profile", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(ADMIN, DELETE, "/api/profile", JSON, true, 405, "METHOD_NOT_ALLOWED"),
        row(USER, GET, "/api/auth/register", JSON, false, 405, "METHOD_NOT_ALLOWED"),
        row(USER, DELETE, "/api/profile", JSON, false, 403, "CSRF_TOKEN_REJECTED"),
        row(USER, POST, "/api/nope", JSON, false, 403, "CSRF_TOKEN_REJECTED"),
        row(USER, GET, "/api/admin/nope", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        row(USER, GET, "/api/admin/fixture", JSON, false, 403, "ACCESS_DENIED"),
        row(ADMIN, GET, "/api/admin/fixture", JSON, false, 200, OK),
        row(ADMIN, GET, "/api/admin/nope", JSON, false, 404, "RESOURCE_NOT_FOUND"),
        // A forgotten grant stays closed, and a grant covers only its own method.
        row(USER, GET, "/api/fixture/ungranted", JSON, false, 403, "ACCESS_DENIED"),
        row(ADMIN, GET, "/api/fixture/ungranted", JSON, false, 403, "ACCESS_DENIED"),
        row(USER, POST, "/api/hello", JSON, true, 403, "ACCESS_DENIED"),
        row(USER, GET, "/api/hello", JSON, false, 200, OK),
        // Every API endpoint produces only JSON, even one that answers without a body.
        row(USER, GET, "/api/hello", HTML, false, 406, NO_CODE),
        row(ADMIN, DELETE, "/api/admin/users/00000000-0000-0000-0000-000000000000", HTML, true, 406, NO_CODE)));
  }

  private static Arguments row(Caller caller, HttpMethod method, String path, String accept, boolean csrf, int status,
      String outcome) {
    return Arguments.of(caller, method, path, accept, csrf, status, outcome);
  }

  @ParameterizedTest(name = "{0} {1} {2} Accept={3} csrf={4} -> {5} {6}")
  @MethodSource("routes")
  void should_answerByPathOwnership_when_anyCallerRequestsAnyPath(Caller caller, HttpMethod method, String path,
      String accept, boolean withCsrf, int expectedStatus, String outcome) throws Exception {
    var request = request(method, path);
    if (accept != null) request.header("Accept", accept);
    if (withCsrf) request.with(csrf());
    if (caller == USER) request.with(user("testuser123").roles("USER"));
    if (caller == ADMIN) request.with(user("testadmin1").roles("ADMIN"));

    var result = mvc.perform(request).andExpect(status().is(expectedStatus));

    if (outcome.equals(SPA)) {
      result.andExpect(content().string(containsString("<div id=\"root\">")))
          .andExpect(header().string("Content-Security-Policy", containsString("'nonce-")));
      return;
    }
    result.andExpect(content().string(not(containsString("<div id=\"root\">"))));
    if (!outcome.equals(OK) && !outcome.equals(NO_CODE)) {
      result.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.code").value(outcome));
    }
  }

  @Test
  void should_reportOnlyTheStatus_when_theLoadBalancerChecksHealth() throws Exception {
    mvc.perform(request(GET, "/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void should_listTheSupportedMethods_when_aMethodIsNotAllowed() throws Exception {
    mvc.perform(request(DELETE, "/api/profile").with(user("testuser123").roles("USER")).with(csrf()))
        .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
    mvc.perform(request(POST, "/home").with(csrf()))
        .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
  }

  @Test
  void should_auditTheCsrfRejection_when_aFrontendPathIsPostedWithoutAToken() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(post("/nope")).andExpect(status().isForbidden());

      assertThat(audit.events()).extracting(event -> AuditLogCapture.fields(event).get("event.action"))
          .contains("csrf-check");
    }
  }

  @Test
  void should_keepTheSession_when_theApiAnswersNotFoundOrWrongMethod() throws Exception {
    userRepository.deleteAll();
    userRepository.save(new UserEntity(UUID.randomUUID(), "testuser123", "testuser123@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
    Cookie session = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");

    mvc.perform(request(GET, "/api/nope").cookie(session)).andExpect(status().isNotFound());
    mvc.perform(request(DELETE, "/api/profile").cookie(session).with(csrf())).andExpect(status().isMethodNotAllowed());

    mvc.perform(request(GET, "/api/profile").cookie(session)).andExpect(status().isOk());
  }
}
