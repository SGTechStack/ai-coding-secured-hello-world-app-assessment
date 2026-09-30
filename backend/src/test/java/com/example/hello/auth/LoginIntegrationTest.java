package com.example.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.common.InvalidCredentialsException;
import com.example.hello.support.IntegrationTestSupport;
import com.example.hello.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Story 2 (plus the session-cookie requirements from INFRA-BE-02). */
class LoginIntegrationTest extends IntegrationTestSupport {

  @Test
  @DisplayName("correct credentials create a session, set a hardened cookie and reset the failure counter")
  void loginSucceeds() throws Exception {
    String username = registerUser("login");
    String ip = uniqueIp();
    attemptLogin(username, "wrong-password-123", ip);
    assertThat(userRepository.findByUsername(username).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);

    ClientSession anonymous = anonymousSession(ip);
    MvcResult result =
        mockMvc
            .perform(jsonRequest(post("/api/auth/login"), anonymous, loginJson(username, GOOD_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn();

    Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).as("session id must rotate on login").isNotEqualTo(anonymous.cookie().getValue());
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");

    User user = userRepository.findByUsername(username).orElseThrow();
    assertThat(user.getFailedLoginAttempts()).isZero();
    assertThat(user.getLockedUntil()).isNull();

    mockMvc
        .perform(get("/api/auth/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.username").value(username));
  }

  @Test
  @DisplayName("wrong password and unknown username produce the same generic 401")
  void genericErrorForWrongPasswordAndUnknownUser() throws Exception {
    String username = registerUser("generic");

    MvcResult wrongPassword = attemptLogin(username, "definitely-not-it-1", uniqueIp());
    MvcResult unknownUser = attemptLogin(uniqueName("ghost"), GOOD_PASSWORD, uniqueIp());

    assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
    assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
    String detailA = JsonPath.read(wrongPassword.getResponse().getContentAsString(), "$.detail");
    String detailB = JsonPath.read(unknownUser.getResponse().getContentAsString(), "$.detail");
    assertThat(detailA).isEqualTo(InvalidCredentialsException.DETAIL).isEqualTo(detailB);

    assertThat(userRepository.findByUsername(username).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
  }

  @Test
  @DisplayName("a locked account is rejected even with the correct password")
  void lockedAccountRejected() throws Exception {
    String username = registerUser("locked");
    User user = userRepository.findByUsername(username).orElseThrow();
    user.lockUntil(Instant.now().plus(Duration.ofMinutes(10)));
    userRepository.save(user);

    MvcResult result = attemptLogin(username, GOOD_PASSWORD, uniqueIp());
    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    String detail = JsonPath.read(result.getResponse().getContentAsString(), "$.detail");
    assertThat(detail).isEqualTo(InvalidCredentialsException.DETAIL);
  }

  @Test
  @DisplayName("anonymous /me reports an unauthenticated session")
  void anonymousMe() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false));
  }
}
