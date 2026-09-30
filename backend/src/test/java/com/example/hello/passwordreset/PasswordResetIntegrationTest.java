package com.example.hello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.support.IntegrationTestSupport;
import com.example.hello.user.User;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Stories 6 and 7. */
class PasswordResetIntegrationTest extends IntegrationTestSupport {

  private static final String NEW_PASSWORD = "Brand-New-Secret-42";

  @Autowired private PasswordResetTokenRepository tokenRepository;

  @Test
  @DisplayName("known and unknown emails get the identical generic response")
  void requestIsEnumerationSafe() throws Exception {
    String username = registerUser("pr");

    MvcResult known = requestReset(username + "@example.com");
    MvcResult unknown = requestReset("nobody-" + username + "@example.com");

    assertThat(known.getResponse().getStatus()).isEqualTo(200);
    assertThat(unknown.getResponse().getStatus()).isEqualTo(200);
    assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
    assertThat(recordingEmailService.sentCount()).as("only the real account gets mail").isEqualTo(1);
  }

  @Test
  @DisplayName("a valid token updates the password once and logs the user out everywhere")
  void confirmResetsPasswordAndInvalidatesSessions() throws Exception {
    String username = registerUser("prc");
    ClientSession existing = login(username, GOOD_PASSWORD);
    mockMvc.perform(get("/api/hello").cookie(existing.cookie())).andExpect(status().isOk());

    requestReset(username + "@example.com");
    String token = recordingEmailService.lastToken();
    assertThat(tokenRepository.findByTokenHash(TokenHasher.hash(token))).isPresent();
    assertThat(tokenRepository.findByTokenHash(token)).as("plaintext token is never stored").isEmpty();

    confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

    PasswordResetToken used = tokenRepository.findByTokenHash(TokenHasher.hash(token)).orElseThrow();
    assertThat(used.getUsedAt()).isNotNull();

    mockMvc
        .perform(get("/api/hello").cookie(existing.cookie()))
        .andExpect(status().isUnauthorized());
    assertThat(attemptLogin(username, GOOD_PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
    assertThat(login(username, NEW_PASSWORD).cookie()).isNotNull();

    confirmReset(token, "Yet-Another-Secret-77")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Reset token is invalid, expired or has already been used"));
  }

  @Test
  @DisplayName("an expired token is rejected and the password is unchanged")
  void expiredTokenRejected() throws Exception {
    String username = registerUser("prx");
    User user = userRepository.findByUsername(username).orElseThrow();
    String token = TokenHasher.generateToken();
    tokenRepository.save(
        new PasswordResetToken(user, TokenHasher.hash(token), Instant.now().minus(Duration.ofMinutes(1))));

    confirmReset(token, NEW_PASSWORD).andExpect(status().isBadRequest());

    assertThat(login(username, GOOD_PASSWORD).cookie()).isNotNull();
  }

  @Test
  @DisplayName("a weak new password is rejected")
  void weakNewPasswordRejected() throws Exception {
    String username = registerUser("prw");
    requestReset(username + "@example.com");

    confirmReset(recordingEmailService.lastToken(), "tiny").andExpect(status().isBadRequest());
    assertThat(login(username, GOOD_PASSWORD).cookie()).isNotNull();
  }

  private MvcResult requestReset(String email) throws Exception {
    ClientSession session = anonymousSession(uniqueIp());
    return mockMvc
        .perform(
            jsonRequest(post("/api/auth/password-reset/request"), session, "{\"email\":\"" + email + "\"}"))
        .andReturn();
  }

  private org.springframework.test.web.servlet.ResultActions confirmReset(String token, String newPassword)
      throws Exception {
    ClientSession session = anonymousSession(uniqueIp());
    return mockMvc.perform(
        jsonRequest(
            post("/api/auth/password-reset/confirm"),
            session,
            "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
  }
}
