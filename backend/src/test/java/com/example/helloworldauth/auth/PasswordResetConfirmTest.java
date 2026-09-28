package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.PasswordResetToken;
import com.example.helloworldauth.user.PasswordResetTokenRepository;
import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the password-reset CONFIRM flow (Story 7).
 *
 * <p>Covers the three acceptance behaviours (valid single-use, expiry rejection,
 * already-used rejection), CSRF enforcement, new-password policy re-validation,
 * and existing-session invalidation. Tokens are persisted directly with the same
 * SHA-256 hashing the request side uses, so the plaintext we submit resolves.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetConfirmTest {

    private static final String OLD_PASSWORD = "correcthorsebattery";
    private static final String NEW_PASSWORD = "brandnewpassword123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordResetTokenRepository tokens;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @BeforeEach
    void seed() {
        tokens.deleteAll();
        users.deleteAll();
        users.save(new User("alice", "alice@example.com", encoder.encode(OLD_PASSWORD), Role.USER));
    }

    @AfterEach
    void cleanup() {
        // Clear reset tokens so a later @SpringBootTest class that deletes users
        // (sharing this H2 context) does not trip the tokens->users FK.
        tokens.deleteAll();
    }

    private User alice() {
        return users.findByEmail("alice@example.com").orElseThrow();
    }

    /** Persists a token for alice with the given expiry and returns its plaintext. */
    private String mintToken(Instant expiresAt) {
        String plaintext = "plaintext-" + System.nanoTime();
        tokens.save(new PasswordResetToken(alice(), sha256Hex(plaintext), expiresAt));
        return plaintext;
    }

    private String confirmBody(String token, String newPassword) {
        return """
            {"token":"%s","newPassword":"%s"}""".formatted(token, newPassword);
    }

    @Test
    void validTokenUpdatesPasswordAndMarksTokenUsed() throws Exception {
        String token = mintToken(Instant.now().plus(Duration.ofMinutes(30)));

        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, NEW_PASSWORD)))
            .andExpect(status().isOk());

        // Password hash now matches the NEW password and no longer the old one.
        User reloaded = alice();
        assertThat(encoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(encoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isFalse();

        // Token is marked used (single-use consumed).
        List<PasswordResetToken> saved = tokens.findByUser(reloaded);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isUsed()).isTrue();
        assertThat(saved.get(0).getUsedAt()).isNotNull();
    }

    @Test
    void expiredTokenIsRejectedAndPasswordUnchanged() throws Exception {
        String token = mintToken(Instant.now().minus(Duration.ofMinutes(1)));

        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, NEW_PASSWORD)))
            .andExpect(status().isBadRequest());

        // Password is unchanged: the old password still verifies, the new one does not.
        User reloaded = alice();
        assertThat(encoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(encoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isFalse();
        // Token is not consumed.
        assertThat(tokens.findByUser(reloaded).get(0).isUsed()).isFalse();
    }

    @Test
    void alreadyUsedTokenIsRejectedOnSecondUse() throws Exception {
        String token = mintToken(Instant.now().plus(Duration.ofMinutes(30)));

        // First use succeeds.
        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, NEW_PASSWORD)))
            .andExpect(status().isOk());

        // Second use of the SAME token is rejected (single-use).
        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, "yetanotherpassword1")))
            .andExpect(status().isBadRequest());

        // The would-be second new password never took effect.
        assertThat(encoder.matches("yetanotherpassword1", alice().getPasswordHash())).isFalse();
        assertThat(encoder.matches(NEW_PASSWORD, alice().getPasswordHash())).isTrue();
    }

    @Test
    void newPasswordShorterThanPolicyIsRejected() throws Exception {
        String token = mintToken(Instant.now().plus(Duration.ofMinutes(30)));

        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, "short")))
            .andExpect(status().isBadRequest());

        // Rejected before any change; old password still verifies.
        assertThat(encoder.matches(OLD_PASSWORD, alice().getPasswordHash())).isTrue();
    }

    @Test
    void missingCsrfIsForbidden() throws Exception {
        String token = mintToken(Instant.now().plus(Duration.ofMinutes(30)));

        mockMvc.perform(post("/api/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, NEW_PASSWORD)))
            .andExpect(status().isForbidden());
    }

    @Test
    void successfulResetInvalidatesExistingSessions() throws Exception {
        // Seed a Spring Session for alice carrying an authenticated security
        // context, exactly as a live login persists it. This populates the
        // principal-name index the confirm flow looks up.
        String sessionId = seedAuthenticatedSession("alice");
        assertThat(sessionRepository.findByPrincipalName("alice")).containsKey(sessionId);

        String token = mintToken(Instant.now().plus(Duration.ofMinutes(30)));
        mockMvc.perform(post("/api/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(token, NEW_PASSWORD)))
            .andExpect(status().isOk());

        // Every existing session for alice is gone.
        assertThat(sessionRepository.findByPrincipalName("alice")).isEmpty();
        assertThat(sessionRepository.findById(sessionId)).isNull();
    }

    /** Creates and saves a session holding an authenticated SPRING_SECURITY_CONTEXT. */
    private String seedAuthenticatedSession(String username) {
        MapSession session = new MapSession();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = new SecurityContextImpl(authentication);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        @SuppressWarnings("unchecked")
        var repo = (org.springframework.session.SessionRepository<MapSession>) sessionRepository;
        repo.save(session);
        return session.getId();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
