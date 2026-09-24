package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.passwordreset.EmailService;
import com.example.helloauth.passwordreset.PasswordResetService;
import com.example.helloauth.passwordreset.PasswordResetToken;
import com.example.helloauth.passwordreset.PasswordResetTokenRepository;
import com.example.helloauth.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Ticket-12 coverage at the HTTP seam: the password-reset flow. Tests drive
 * the real path like the SPA — CSRF bootstrap via {@code GET /api/auth/csrf},
 * {@code X-XSRF-TOKEN} on every mutation — through the shared
 * {@link ApiTestSupport} fixture, with two additions (ticket 05 strategy):
 * time runs on a {@code @Primary} {@link MutableClock}, and the plaintext
 * token is captured at the {@link EmailService} port (the stubbed email is
 * its only egress). Token fixtures are seeded through
 * {@link PasswordResetTokenRepository} for the expiry/single-use cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTests extends ApiTestSupport {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String GENERIC_MESSAGE =
        "If an account with that email exists, we've sent a reset link.";

    @Autowired
    IpThrottleService ipThrottle;

    @Autowired
    MutableClock clock;

    /**
     * The emailed token exists only inside the stub's send call — the mock is
     * how the test observes it (the port seam). The real stub's log line is
     * pinned separately in {@code EmailServiceTests}.
     */
    @MockitoBean
    EmailService emailService;

    /**
     * Time control: token expiry is measured against the injected Clock, so
     * tests advance time instead of sleeping.
     */
    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(T0);
        }
    }

    @BeforeEach
    void clean() {
        // Tokens first — password_reset_tokens.user_id FKs into users.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        ipThrottle.clear();
        clock.setInstant(T0);
    }

    // ------------------------------------------------------------------
    // Request endpoint — enumeration resistance
    // ------------------------------------------------------------------

    @Test
    void requestReturnsIdenticalGenericMessageForKnownAndUnknownEmail() throws Exception {
        seedUser("alice", "alice@example.com");

        String knownBody = requestReset("alice@example.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
            .andReturn().getResponse().getContentAsString();
        String unknownBody = requestReset("ghost@example.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
            .andReturn().getResponse().getContentAsString();

        // Byte-identical bodies — account existence must not be inferable.
        assertThat(knownBody).isEqualTo(unknownBody);
    }

    @Test
    void requestForUnknownEmailCreatesNoTokenAndSendsNoEmail() throws Exception {
        requestReset("ghost@example.com").andExpect(status().isOk());

        assertThat(tokenRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void requestRejectsOverLengthEmail() throws Exception {
        // F-05 ceiling: 255 chars but still a syntactically valid address —
        // it is the @Size bound (254), not @Email, that rejects it.
        requestReset("a".repeat(243) + "@example.com")
            .andExpect(status().isBadRequest());

        assertThat(tokenRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    // ------------------------------------------------------------------
    // Token creation — only the hash is ever stored
    // ------------------------------------------------------------------

    @Test
    void requestForRegisteredEmailStoresHashedTokenAndEmailsTheLink() throws Exception {
        User alice = seedUser("alice", "alice@example.com");

        requestReset("alice@example.com").andExpect(status().isOk());

        // The plaintext token's only egress is the stubbed email — capture it
        // at the port to prove the end-to-end linkage.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLink(
            eq("alice@example.com"), tokenCaptor.capture());
        String emailedToken = tokenCaptor.getValue();
        assertThat(emailedToken).isNotBlank();

        // The stored row is keyed by the SHA-256 hash of what was emailed…
        PasswordResetToken stored = tokenRepository
            .findByTokenHash(PasswordResetService.hashToken(emailedToken))
            .orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(alice.getId());
        // The row really persisted — the IDENTITY id pins getId() too.
        assertThat(stored.getId()).isPositive();
        // …never the plaintext itself (a DB read must not yield a replayable
        // credential). 64 lowercase hex chars = SHA-256.
        assertThat(stored.getTokenHash())
            .isNotEqualTo(emailedToken)
            .hasSize(64)
            .matches("[0-9a-f]+");
        // Expiry inside the spec's 15–30 min bound; the clock pins it exactly.
        assertThat(stored.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(stored.getUsedAt()).isNull();
        // The plaintext finds nothing — only its hash is stored.
        assertThat(tokenRepository.findByTokenHash(emailedToken)).isEmpty();
    }

    @Test
    void eachRequestMintsADistinctToken() throws Exception {
        seedUser("alice", "alice@example.com");

        // Two requests for the same account must mint two independent
        // credentials — a constant/degenerate token (e.g. the CSPRNG never
        // stirred in) would collide on the token_hash unique constraint and
        // hand every requester the same replayable link.
        requestReset("alice@example.com").andExpect(status().isOk());
        requestReset("alice@example.com").andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendPasswordResetLink(
            eq("alice@example.com"), tokenCaptor.capture());
        assertThat(tokenCaptor.getAllValues())
            .hasSize(2)
            .doesNotHaveDuplicates();
        assertThat(tokenRepository.count()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Confirm — the happy path changes the password and kills sessions
    // ------------------------------------------------------------------

    @Test
    void confirmWithValidTokenUpdatesPasswordMarksUsedAndKillsSessions()
            throws Exception {
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);

        // End-to-end: the token reaches the user only via the stubbed email.
        requestReset("alice@example.com").andExpect(status().isOk());
        String token = emailedToken(emailService);

        confirmReset(token, NEW_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").isNotEmpty());

        // The token is spent — single-use marker stamped.
        PasswordResetToken stored = tokenRepository
            .findByTokenHash(PasswordResetService.hashToken(token))
            .orElseThrow();
        assertThat(stored.getUsedAt()).isEqualTo(T0);

        // Every pre-existing session is dead: a replayed cookie gets 401.
        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hello").cookie(session))
            .andExpect(status().isUnauthorized());

        // Old password no longer works; the new one does.
        login("alice", VALID_PASSWORD).andExpect(status().isUnauthorized());
        login("alice", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmAcceptsPasswordAtExactlyTheMinimumLength() throws Exception {
        seedUser("alice", "alice@example.com");
        requestReset("alice@example.com").andExpect(status().isOk());
        String token = emailedToken(emailService);

        // Boundary value: exactly the 12-char policy floor must be ACCEPTED
        // (< minLength rejects; <= would wrongly burn the token here).
        String atMinimum = "x".repeat(12);
        confirmReset(token, atMinimum).andExpect(status().isOk());

        login("alice", atMinimum).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Confirm — rejections
    // ------------------------------------------------------------------

    @Test
    void confirmRejectsExpiredTokenAndLeavesPasswordUnchanged() throws Exception {
        User alice = seedUser("alice", "alice@example.com");
        String token = "expired-token-value";
        seedToken(alice, token, T0.plus(Duration.ofMinutes(15)));

        // One minute past expiry — the Clock bean controls time, no sleeping.
        clock.advance(Duration.ofMinutes(16));

        confirmReset(token, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Content-Type",
                Matchers.containsString("application/problem+json")))
            .andExpect(jsonPath("$.title").value("Password reset failed"))
            .andExpect(jsonPath("$.detail")
                .value("This reset link is invalid or has expired."));

        // Password unchanged — the old one still logs in, and the expired
        // token stays unused (it cannot be revived).
        login("alice", VALID_PASSWORD).andExpect(status().isOk());
        assertThat(tokenRepository
            .findByTokenHash(PasswordResetService.hashToken(token))
            .orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void confirmRejectsReusedToken() throws Exception {
        User alice = seedUser("alice", "alice@example.com");
        String token = "single-use-token";
        seedToken(alice, token, T0.plus(Duration.ofMinutes(15)));

        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        // The spent token is dead — the same generic rejection, and the
        // replayed attempt must not move the password again.
        confirmReset(token, "a different new pass")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Password reset failed"))
            .andExpect(jsonPath("$.detail")
                .value("This reset link is invalid or has expired."));

        login("alice", "a different new pass")
            .andExpect(status().isUnauthorized());
        login("alice", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmRejectsShortPasswordAndKeepsTokenUsable() throws Exception {
        User alice = seedUser("alice", "alice@example.com");
        String token = "short-pass-token";
        seedToken(alice, token, T0.plus(Duration.ofMinutes(15)));

        confirmReset(token, "too short")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Password too short"));

        // The failed attempt must not consume the token — the user can retry
        // with a compliant password, and the old password still works.
        assertThat(tokenRepository
            .findByTokenHash(PasswordResetService.hashToken(token))
            .orElseThrow().getUsedAt()).isNull();
        login("alice", VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmRejectsUnknownToken() throws Exception {
        seedUser("alice", "alice@example.com");

        confirmReset("no-such-token", NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Password reset failed"))
            .andExpect(jsonPath("$.detail")
                .value("This reset link is invalid or has expired."));

        login("alice", VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmRejectsOverLengthFieldsAndKeepsTokenUsable() throws Exception {
        User alice = seedUser("alice", "alice@example.com");
        String token = "within-ceiling-token";
        seedToken(alice, token, T0.plus(Duration.ofMinutes(15)));

        // F-05 ceilings at the DTO boundary — a 129-char token is 400
        // before any lookup, and a 129-char newPassword is 400 without
        // consuming the token (the same don't-burn-the-link contract as
        // the too-short case).
        confirmReset("t".repeat(129), NEW_PASSWORD)
            .andExpect(status().isBadRequest());
        confirmReset(token, "p".repeat(129))
            .andExpect(status().isBadRequest());

        assertThat(tokenRepository
            .findByTokenHash(PasswordResetService.hashToken(token))
            .orElseThrow().getUsedAt()).isNull();
        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // CSRF — both endpoints are state-changing
    // ------------------------------------------------------------------

    @Test
    void passwordResetEndpointsRequireCsrf() throws Exception {
        // A tokenless mutation is rejected by the filter chain before any
        // reset logic runs — same contract as login/register/logout.
        mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isForbidden());
    }
}
