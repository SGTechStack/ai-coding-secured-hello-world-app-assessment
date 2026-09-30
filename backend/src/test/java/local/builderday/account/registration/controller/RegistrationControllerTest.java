package local.builderday.account.registration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.Accounts;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RegistrationControllerTest {
  private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
  private static final String VALID_PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;

  @BeforeEach
  void resetState() {
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @Test
  void should_createEnabledUserAccountWithBcryptHash_andNotAuthenticate_whenSubmissionIsValid() throws Exception {
    var result = register("{\"username\":\"  TestUser123 \",\"email\":\" TestUser123@Test.Example.com \","
        + "\"password\":\"" + VALID_PASSWORD + "\"}")
        .andExpect(status().isCreated())
        .andExpect(content().json("{\"message\":\"Account created. You can now log in.\"}", JsonCompareMode.STRICT))
        .andReturn();

    var account = userRepository.findByUsername("testuser123").orElseThrow();
    assertThat(account.getEmail()).isEqualTo("testuser123@test.example.com");
    assertThat(account.getRole()).isEqualTo("USER");
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getPasswordHash()).startsWith("$2").doesNotContain(VALID_PASSWORD);
    assertThat(passwordEncoder.matches(VALID_PASSWORD, account.getPasswordHash())).isTrue();
    // No authentication: the Session restore probe still refuses this session.
    mvc.perform(get("/api/profile").cookie(sessionCookie(result.getRequest().getCookies())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_storePasswordUnaltered_whenItHasLeadingAndTrailingSpaces() throws Exception {
    register(body("testuser123", "testuser123@test.example.com", "  Leading1Aa  ")).andExpect(status().isCreated());

    var hash = userRepository.findByUsername("testuser123").orElseThrow().getPasswordHash();
    assertThat(passwordEncoder.matches("  Leading1Aa  ", hash)).isTrue();
    assertThat(passwordEncoder.matches("Leading1Aa", hash)).isFalse();
  }

  @Test
  void should_rejectDuplicatesCaseInsensitively_includingSoftDeletedAccounts_withoutNamingTheField() throws Exception {
    register(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD)).andExpect(status().isCreated());
    var deleted = new UserEntity(UUID.randomUUID(), "deleteduser", "deleted@test.example.com", "$2a$12$unused",
        "USER", true);
    Accounts.markDeleted(deleted, Instant.now());
    userRepository.save(deleted);

    for (String duplicate : List.of(
        body("TESTUSER123", "other@test.example.com", VALID_PASSWORD),
        body("otheruser1", "TestUser123@TEST.example.com", VALID_PASSWORD),
        body("DeletedUser", "fresh@test.example.com", VALID_PASSWORD),
        body("freshuser1", "DELETED@test.example.com", VALID_PASSWORD))) {
      register(duplicate)
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(content().json(
              "{\"status\":400,\"code\":\"REGISTRATION_REJECTED\",\"errors\":[{\"code\":\"USER_EXISTS\"}]}"))
          .andExpect(jsonPath("$.errors[0].field").doesNotExist());
    }
    assertThat(userRepository.count()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
      "missing username       | {\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"}           | username | FIELD_REQUIRED",
      "non-string username    | {\"username\":42,\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"} | username | FIELD_REQUIRED",
      "missing email          | {\"username\":\"testuser123\","
          + "\"password\":\"Str0ng!Passw0rd\"}                         | email    | FIELD_REQUIRED",
      "missing password       | {\"username\":\"testuser123\","
          + "\"email\":\"testuser123@test.example.com\"}               | password | FIELD_REQUIRED",
      "short username         | {\"username\":\"abcd\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"} | username | USERNAME_TOO_SHORT",
      "username with space    | {\"username\":\"test user\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"} | username | USERNAME_INVALID_CHARACTER",
      "username leading dot   | {\"username\":\".testuser\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"} | username | USERNAME_INVALID_CHARACTER",
      "username with slash    | {\"username\":\"test/user\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Str0ng!Passw0rd\"} | username | USERNAME_INVALID_CHARACTER",
      "invalid email          | {\"username\":\"testuser123\",\"email\":\"not-an-email\","
          + "\"password\":\"Str0ng!Passw0rd\"} | email    | EMAIL_INVALID",
      "73-character password  | {\"username\":\"testuser123\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!x\"}"
          + " | password | PASSWORD_TOO_LONG",
      "password missing digit | {\"username\":\"testuser123\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"NoDigitsHere!!\"} | password | PASSWORD_MISSING_DIGIT",
      "password has username  | {\"username\":\"testuser123\",\"email\":\"someone@test.example.com\","
          + "\"password\":\"Xx!TESTUSER123\"} | password | PASSWORD_CONTAINS_IDENTITY",
      "non-ASCII password     | {\"username\":\"testuser123\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"P\u00e4ssword1234!\"} | password | PASSWORD_INVALID_CHARACTER",
      "common password        | {\"username\":\"testuser123\",\"email\":\"testuser123@test.example.com\","
          + "\"password\":\"Unbelievable\"} | password | PASSWORD_TOO_COMMON"})
  void should_rejectWithStableFieldCode_andCreateNoAccount(String name, String request, String field, String code)
      throws Exception {
    var response = register(request)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andReturn().getResponse().getContentAsString();

    List<Map<String, String>> errors = JsonPath.read(response, "$.errors");
    assertThat(errors).contains(Map.of("field", field, "code", code));
    assertThat(response).doesNotContain("testuser123@", "Str0ng", "Unbelievable", "test user");
    assertThat(userRepository.count()).isZero();
  }

  @Test
  void should_reportEveryViolatedRuleTogether() throws Exception {
    var response = register(body("ab", "bad", "qzxwvk")).andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();

    List<String> codes = JsonPath.read(response, "$.errors[*].code");
    assertThat(codes).containsExactlyInAnyOrder("USERNAME_TOO_SHORT", "EMAIL_INVALID", "PASSWORD_TOO_SHORT",
        "PASSWORD_MISSING_UPPERCASE", "PASSWORD_MISSING_DIGIT", "PASSWORD_MISSING_SPECIAL");
  }

  @Test
  void should_rejectOverlongIdentifiers() throws Exception {
    String longEmail = "testuser@" + ("a".repeat(60) + ".").repeat(4) + "example.com";
    var response = register(body("a".repeat(101), longEmail, VALID_PASSWORD)).andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();

    List<String> codes = JsonPath.read(response, "$.errors[*].code");
    assertThat(codes).containsExactlyInAnyOrder("USERNAME_TOO_LONG", "EMAIL_TOO_LONG");
  }

  @Test
  void should_refuseUnknownFields_includingRole_ratherThanIgnoreThem() throws Exception {
    register("{\"username\":\"testuser123\",\"email\":\"testuser123@test.example.com\",\"password\":\"" + VALID_PASSWORD
        + "\",\"role\":\"ADMIN\"}")
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"errors\":[{\"code\":\"FIELD_NOT_ALLOWED\"}]}"));
    assertThat(userRepository.count()).isZero();
  }

  @Test
  void should_rejectMalformedBodies_withTheStableRejectionCode_andCreateNoAccount() throws Exception {
    for (String malformed : List.of("{not json", "[]", "\"text\"")) {
      register(malformed)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("REGISTRATION_REJECTED"))
          .andExpect(jsonPath("$.errors").isEmpty());
    }
    assertThat(userRepository.count()).isZero();
  }

  @Test
  void should_rejectBodiesOverFourKilobytes() throws Exception {
    register(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD + " ".repeat(4096)))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"errors\":[{\"code\":\"REQUEST_TOO_LARGE\"}]}"));
    assertThat(userRepository.count()).isZero();
  }

  @Test
  void should_requireCsrfToken_andAcceptOnlyJson() throws Exception {
    var csrf = mvc.perform(get("/csrf")).andReturn();
    var cookie = csrf.getResponse().getCookie("id");
    String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
    String valid = body("testuser123", "testuser123@test.example.com", VALID_PASSWORD);

    mvc.perform(post("/api/auth/register").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(valid))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/register").cookie(cookie).header("X-CSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("username=testuser123"))
        .andExpect(status().isUnsupportedMediaType());
    assertThat(userRepository.count()).isZero();
  }

  @Test
  void should_lockSourceAfterTenRejections_withGeneric429AndNoRetryAfter() throws Exception {
    String ip = "198.51.100.77";
    for (int attempt = 1; attempt <= 10; attempt++) {
      register(body("ab", "testuser123@test.example.com", VALID_PASSWORD), ip).andExpect(status().isBadRequest());
    }

    try (var audit = new AuditLogCapture("audit")) {
      register(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD), ip)
          .andExpect(status().isTooManyRequests())
          .andExpect(header().doesNotExist("Retry-After"))
          .andExpect(jsonPath("$.detail").value("Registration is temporarily unavailable. Please try again later."))
          .andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"))
          .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(ip))));
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.reason", "rate_limited")
            .containsEntry("source.ip", ip);
      });
    }
    assertThat(userRepository.count()).isZero();
    // Another source is unaffected.
    register(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD)).andExpect(status().isCreated());
  }

  @Test
  void should_emitSafeStructuredAuditEvents_forSuccessAndRejection() throws Exception {
    String ip = "203.0.113.50";
    try (var audit = new AuditLogCapture("audit")) {
      register(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD), ip).andExpect(status().isCreated());
      register(body("evil\r\nINJECTED user", "evil\r\n@test.example.com", "zqpass\r\nzq"), ip)
          .andExpect(status().isBadRequest());

      var events = audit.events();
      assertThat(events).hasSize(2);
      var success = AuditLogCapture.fields(events.get(0));
      assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
      assertThat(success).containsEntry("event.action", "user-registration")
          .containsEntry("event.category", List.of("iam"))
          .containsEntry("event.type", List.of("creation"))
          .containsEntry("event.outcome", "success")
          .containsEntry("url.path", "/api/auth/register")
          .containsEntry("http.request.method", "POST")
          .containsEntry("source.ip", ip)
          .containsEntry("user.id", userRepository.findByUsername("testuser123").orElseThrow().getId().toString())
          .containsKeys("trace.id", "session.hash");
      assertThat((String) success.get("session.hash")).matches("[0-9a-f]{64}");

      var rejection = AuditLogCapture.fields(events.get(1));
      assertThat(events.get(1).getLevel()).isEqualTo(Level.WARN);
      assertThat(rejection).containsEntry("event.outcome", "failure").containsEntry("event.reason", "rejected")
          .containsKey("error.code").doesNotContainKey("user.id");

      for (var event : events) {
        String rendered = event.getFormattedMessage() + AuditLogCapture.fields(event);
        assertThat(rendered).doesNotContain("testuser123", "Str0ng", "INJECTED", "evil", "zqpass", "\r", "\n",
            "X-CSRF-TOKEN");
      }
    }
  }

  private static String body(String username, String email, String password) {
    return "{\"username\":\"" + json(username) + "\",\"email\":\"" + json(email)
        + "\",\"password\":\"" + json(password) + "\"}";
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
  }

  /** Each call uses a fresh documentation-range source IP so unrelated rejections never share a rate-limit bucket. */
  private ResultActions register(String requestBody) throws Exception {
    return register(requestBody, "192.0.2." + (NEXT_IP.getAndIncrement() % 250 + 1));
  }

  private ResultActions register(String requestBody, String remoteAddress) throws Exception {
    var csrf = mvc.perform(get("/csrf")).andExpect(status().isOk()).andReturn();
    String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
    return mvc.perform(post("/api/auth/register").cookie(csrf.getResponse().getCookie("id"))
        .header("X-CSRF-TOKEN", token)
        .contentType(MediaType.APPLICATION_JSON).content(requestBody)
        .with(request -> { request.setRemoteAddr(remoteAddress); return request; }));
  }

  private static Cookie sessionCookie(Cookie[] cookies) {
    for (var cookie : cookies) if (cookie.getName().equals("id")) return cookie;
    throw new AssertionError("Request carried no session cookie.");
  }
}
