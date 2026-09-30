package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.domain.PasswordResetToken;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.repository.PasswordResetTokenRepository;
import com.assessment.securedhelloworld.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers PRD Stories 6-7 / ticket 04. The plaintext reset token only ever exists inside the
 * (stub) emailed link, which this test cannot observe from the outside without reaching into the
 * logger — so tests that need a real, confirmable token instead compute one independently and
 * insert a {@link PasswordResetToken} row with its hash directly via the repository (same
 * SHA-256/hex scheme {@code PasswordResetService} uses), then exercise the real HTTP contract of
 * the confirm endpoint via MockMvc. Session cookies are asserted via the raw "SESSION"
 * Set-Cookie value, matching {@code AuthIntegrationTest}'s convention.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private static String hashToken(String plaintextToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(plaintextToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void registerUser(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated());
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    /** Inserts a token row with a known plaintext/hash pair, bypassing the emailed-link stub. */
    private String seedResetToken(String username, Duration validFor) throws Exception {
        User user = userRepository.findByUsername(username).orElseThrow();
        String plaintextToken = "test-token-" + java.util.UUID.randomUUID();
        PasswordResetToken token = new PasswordResetToken(user, hashToken(plaintextToken), Instant.now().plus(validFor));
        passwordResetTokenRepository.save(token);
        return plaintextToken;
    }

    @Test
    void requestReturnsIdenticalResponseForRegisteredAndUnregisteredEmail() throws Exception {
        registerUser("resetuser1", "resetuser1@example.com", "correct-horse-battery");

        MvcResult registeredResult = mockMvc.perform(post("/api/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "resetuser1@example.com"))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult unregisteredResult = mockMvc.perform(post("/api/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-such-address@example.com"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(registeredResult.getResponse().getStatus()).isEqualTo(unregisteredResult.getResponse().getStatus());
        assertThat(registeredResult.getResponse().getContentAsString())
                .isEqualTo(unregisteredResult.getResponse().getContentAsString());
    }

    @Test
    void requestForRegisteredEmailPersistsOnlyTokenHash() throws Exception {
        registerUser("resetuser2", "resetuser2@example.com", "correct-horse-battery");

        mockMvc.perform(post("/api/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "resetuser2@example.com"))))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("resetuser2").orElseThrow();
        assertThat(passwordResetTokenRepository.findAll())
                .anySatisfy(token -> {
                    assertThat(token.getUser().getId()).isEqualTo(user.getId());
                    assertThat(token.getTokenHash()).hasSize(64); // SHA-256 hex digest length
                    assertThat(token.isUsed()).isFalse();
                    assertThat(token.isExpired()).isFalse();
                });
    }

    @Test
    void confirmHappyPathChangesPasswordAndMarksTokenUsed() throws Exception {
        registerUser("resetuser3", "resetuser3@example.com", "old-correct-password");
        String plaintextToken = seedResetToken("resetuser3", Duration.ofMinutes(30));

        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", plaintextToken, "newPassword", "brand-new-correct-password"))))
                .andExpect(status().isOk());

        // Old password no longer logs in.
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "resetuser3", "password", "old-correct-password"))))
                .andExpect(status().isUnauthorized());

        // New password logs in.
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "resetuser3", "password", "brand-new-correct-password"))))
                .andExpect(status().isOk());

        PasswordResetToken persisted = passwordResetTokenRepository.findByTokenHash(hashToken(plaintextToken)).orElseThrow();
        assertThat(persisted.isUsed()).isTrue();
    }

    @Test
    void confirmingTheSameTokenTwiceFailsOnSecondAttempt() throws Exception {
        registerUser("resetuser4", "resetuser4@example.com", "old-correct-password");
        String plaintextToken = seedResetToken("resetuser4", Duration.ofMinutes(30));

        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", plaintextToken, "newPassword", "brand-new-correct-password"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", plaintextToken, "newPassword", "another-new-password-2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOKEN_ALREADY_USED"));
    }

    @Test
    void confirmingAnExpiredTokenFailsAndPasswordUnchanged() throws Exception {
        registerUser("resetuser5", "resetuser5@example.com", "old-correct-password");
        String plaintextToken = seedResetToken("resetuser5", Duration.ofMinutes(-5)); // already expired

        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", plaintextToken, "newPassword", "brand-new-correct-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOKEN_EXPIRED"));

        // Old password still works; password was not changed.
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "resetuser5", "password", "old-correct-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void confirmingAnUnknownTokenFails() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "not-a-real-token", "newPassword", "brand-new-correct-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void resetInvalidatesSessionsObtainedBeforeTheReset() throws Exception {
        registerUser("resetuser6", "resetuser6@example.com", "old-correct-password");
        Cookie sessionCookie = login("resetuser6", "old-correct-password");

        // Prove the session works before the reset.
        mockMvc.perform(get("/api/hello").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, resetuser6"));

        String plaintextToken = seedResetToken("resetuser6", Duration.ofMinutes(30));
        mockMvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", plaintextToken, "newPassword", "brand-new-correct-password"))))
                .andExpect(status().isOk());

        // The session captured before the reset must now be rejected.
        mockMvc.perform(get("/api/hello").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }
}
