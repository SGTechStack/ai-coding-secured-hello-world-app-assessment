package local.builderday.auth.login.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.support.Accounts;
import local.builderday.account.core.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "app.security.login.rate-limit.attempts=3")
@AutoConfigureMockMvc
class LoginControllerTest {
  private static final int RATE_LIMIT = 3;

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @MockitoSpyBean UserService userService;

  // Every test context shares one H2 database, and a bucket keeps the limit it was created with, so this class's low
  // limit must not leak into other classes' buckets (or theirs into this one).
  @AfterEach
  void clearRateLimits() {
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @BeforeEach
  void setUpUser() {
    rateLimitBucketRepository.deleteAllInBatch();
    userRepository.deleteAll();
    userRepository.save(new UserEntity(UUID.randomUUID(), "johndoe", null, passwordEncoder.encode("Password123!"),
        "USER", true));
  }

  @Test
  void should_authenticateAndRotateSessionAndCsrfToken_whenCredentialsAndAnonymousCsrfAreValid() throws Exception {
    var csrfBootstrap = mvc.perform(get("/csrf")).andExpect(status().isOk()).andReturn();
    var anonymousCookie = csrfBootstrap.getResponse().getCookie("id");
    String anonymousToken = JsonPath.read(csrfBootstrap.getResponse().getContentAsString(), "$.token");

    var login = mvc.perform(post("/api/auth/login").cookie(anonymousCookie)
            .header("X-CSRF-TOKEN", anonymousToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"johndoe\",\"password\":\"Password123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.username").value("johndoe"))
        .andExpect(jsonPath("$.csrf").doesNotExist())
        .andReturn();

    // Sessions live in Spring Session JDBC, so the session is identified by the "id" cookie.
    var authenticatedCookie = login.getResponse().getCookie("id");
    assertThat(authenticatedCookie).isNotNull();
    assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymousCookie.getValue());
    // The anonymous token died with its session: the authenticated session refuses it as a CSRF failure (403).
    mvc.perform(post("/api/auth/register").cookie(authenticatedCookie).header("X-CSRF-TOKEN", anonymousToken)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    // The client fetches the authenticated session's token from /csrf. It passes CSRF on the next mutating request:
    // the empty body is rejected on its merits (400), not refused as a CSRF failure (403).
    var csrfAfterLogin = mvc.perform(get("/csrf").cookie(authenticatedCookie)).andExpect(status().isOk()).andReturn();
    String replacementToken = JsonPath.read(csrfAfterLogin.getResponse().getContentAsString(), "$.token");
    mvc.perform(post("/api/auth/register").cookie(authenticatedCookie).header("X-CSRF-TOKEN", replacementToken)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    // The anonymous session was invalidated: reusing its cookie starts a fresh session.
    var reuse = mvc.perform(get("/csrf").cookie(anonymousCookie)).andExpect(status().isOk()).andReturn();
    assertThat(reuse.getResponse().getCookie("id")).isNotNull();
  }

  @Test
  void should_carryTheStoredRoleInTheProfile_when_aUserOrAnAdminLogsIn() throws Exception {
    var userId = userRepository.findByUsername("johndoe").orElseThrow().getId();
    var adminId = UUID.randomUUID();
    userRepository.save(new UserEntity(adminId, "janeadmin", null, passwordEncoder.encode("Password123!"),
        "ADMIN", true));

    mvc.perform(loginRequest(credentials("johndoe", "Password123!")).with(from("203.0.113.30")))
        .andExpect(status().isOk())
        .andExpect(content().json(
            "{\"profile\":{\"id\":\"" + userId + "\",\"username\":\"johndoe\",\"role\":\"USER\"}}", JsonCompareMode.STRICT));
    mvc.perform(loginRequest(credentials("janeadmin", "Password123!")).with(from("203.0.113.31")))
        .andExpect(status().isOk())
        .andExpect(content().json(
            "{\"profile\":{\"id\":\"" + adminId + "\",\"username\":\"janeadmin\",\"role\":\"ADMIN\"}}", JsonCompareMode.STRICT));
  }

  @Test
  void should_returnSafeProblemDetailWithoutAuthenticating_whenLoginRequestIsMalformed() throws Exception {
    for (String request : List.of(
        "{}",
        "{\"username\":\"\",\"password\":\"\"}",
        "{\"username\":\"john\",\"password\":\"valid password\"}",
        "{\"username\":\"bad/name\",\"password\":\"valid password\"}",
        "{\"username\":\"bad\\\\name\",\"password\":\"valid password\"}",
        "{\"username\":\"" + "a".repeat(129) + "\",\"password\":\"valid password\"}",
        "{\"username\":\"johndoe\",\"password\":\"" + "p".repeat(1025) + "\"}")) {
      clearInvocations(userService);
      var response = mvc.perform(loginRequest(request))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.detail").value("Request validation failed."))
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andReturn().getResponse();

      assertThat(response.getContentAsString()).doesNotContain("bad/name", "bad\\name");
      verifyNoInteractions(userService);
    }
  }

  @Test
  void should_returnAuthenticationUnavailableWithoutRetryAfter_when_theSourceIpExceedsTheLoginRateLimit()
      throws Exception {
    for (int attempt = 0; attempt < RATE_LIMIT; attempt++) {
      mvc.perform(loginRequest(credentials("johndoe", "Password123!")).with(from("203.0.113.20")))
          .andExpect(status().isOk());
    }

    mvc.perform(loginRequest(credentials("johndoe", "Password123!")).with(from("203.0.113.20")))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
        .andExpect(header().doesNotExist("Retry-After"));
  }

  @Test
  void should_returnTheWrongPasswordBody_when_theAccountIsLocked() throws Exception {
    String wrongPassword = null;
    // One source IP per attempt, so this class's low Login rate limit never interferes.
    for (int attempt = 0; attempt < 5; attempt++) {
      wrongPassword = rejectedBody(credentials("johndoe", "Wrong-Password1"), "198.51.100." + attempt);
    }

    assertThat(rejectedBody(credentials("johndoe", "Password123!"), "198.51.100.99")).isEqualTo(wrongPassword);
  }

  @Test
  void should_returnTheWrongPasswordBody_when_theAccountIsDisabledOrUnknown() throws Exception {
    String wrongPassword = rejectedBody(credentials("johndoe", "Wrong-Password1"));
    var user = userRepository.findByUsername("johndoe").orElseThrow();
    Accounts.disable(user, java.time.Instant.now());
    userRepository.save(user);

    assertThat(rejectedBody(credentials("johndoe", "Password123!"))).isEqualTo(wrongPassword);
    assertThat(rejectedBody(credentials("nobody123", "Password123!"))).isEqualTo(wrongPassword);
  }

  private String rejectedBody(String credentials) throws Exception {
    return rejectedBody(credentials, "127.0.0.1");
  }

  private String rejectedBody(String credentials, String sourceIp) throws Exception {
    return mvc.perform(loginRequest(credentials).with(from(sourceIp)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  void should_notCountTowardsTheLoginRateLimit_when_theLoginRequestIsMalformed() throws Exception {
    for (int attempt = 0; attempt < RATE_LIMIT + 1; attempt++) {
      mvc.perform(loginRequest("{}").with(from("203.0.113.21"))).andExpect(status().isBadRequest());
    }

    mvc.perform(loginRequest(credentials("johndoe", "Password123!")).with(from("203.0.113.21")))
        .andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder loginRequest(String request) {
    return post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request);
  }

  private static String credentials(String username, String password) {
    return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
  }

  private static RequestPostProcessor from(String sourceIp) {
    return request -> {
      request.setRemoteAddr(sourceIp);
      return request;
    };
  }
}
