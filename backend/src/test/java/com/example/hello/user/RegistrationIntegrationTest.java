package com.example.hello.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Story 1. */
class RegistrationIntegrationTest extends IntegrationTestSupport {

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("valid registration creates an enabled USER with a BCrypt hash")
  void registersUser() throws Exception {
    String username = uniqueName("reg");
    ClientSession session = anonymousSession(uniqueIp());

    mockMvc
        .perform(
            jsonRequest(
                post("/api/auth/register"),
                session,
                registerJson(username, username + "@Example.com", GOOD_PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    User stored = userRepository.findByUsername(username).orElseThrow();
    assertThat(stored.getRole()).isEqualTo(Role.USER);
    assertThat(stored.isEnabled()).isTrue();
    assertThat(stored.getEmail()).isEqualTo(username + "@example.com");
    assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(GOOD_PASSWORD);
    assertThat(passwordEncoder.matches(GOOD_PASSWORD, stored.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("duplicate username is rejected with 409 and no second account")
  void rejectsDuplicateUsername() throws Exception {
    String username = registerUser("dup");
    ClientSession session = anonymousSession(uniqueIp());

    mockMvc
        .perform(
            jsonRequest(
                post("/api/auth/register"),
                session,
                registerJson(username, "other-" + username + "@example.com", GOOD_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("username"));

    assertThat(userRepository.findByEmailIgnoreCase("other-" + username + "@example.com")).isEmpty();
  }

  @Test
  @DisplayName("duplicate email (case-insensitive) is rejected with 409")
  void rejectsDuplicateEmail() throws Exception {
    String username = registerUser("dupmail");
    ClientSession session = anonymousSession(uniqueIp());

    mockMvc
        .perform(
            jsonRequest(
                post("/api/auth/register"),
                session,
                registerJson(username + "2", username.toUpperCase() + "@EXAMPLE.COM", GOOD_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("email"));

    assertThat(userRepository.existsByUsername(username + "2")).isFalse();
  }

  @Test
  @DisplayName("password shorter than 12 characters is rejected with 400 and no account")
  void rejectsWeakPassword() throws Exception {
    String username = uniqueName("weak");
    ClientSession session = anonymousSession(uniqueIp());

    mockMvc
        .perform(
            jsonRequest(
                post("/api/auth/register"),
                session,
                registerJson(username, username + "@example.com", "short-pw-1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    assertThat(userRepository.existsByUsername(username)).isFalse();
  }

  @Test
  @DisplayName("registration without a CSRF token is refused")
  void requiresCsrf() throws Exception {
    String username = uniqueName("csrf");
    mockMvc
        .perform(
            post("/api/auth/register")
                .with(remoteAddr(uniqueIp()))
                .contentType("application/json")
                .content(registerJson(username, username + "@example.com", GOOD_PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("CSRF token missing or invalid"));

    assertThat(userRepository.existsByUsername(username)).isFalse();
  }
}
