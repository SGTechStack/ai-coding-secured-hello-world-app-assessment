package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limiting previously covered {@code POST /api/auth/login} and nothing
 * else, leaving registration and both password-reset endpoints open. Each runs
 * a BCrypt hash, so an unauthenticated caller could spend server CPU at
 * negligible cost, fill the users table, or issue unlimited reset tokens
 * against somebody else's address.
 *
 * <p>Limits are tightened via properties so the thresholds can be reached in a
 * test without hundreds of requests; the configured production values are
 * asserted separately in {@link
 * com.sgtechstack.helloworldauthapp.config.SecurityPropertiesTest}.
 */
@SpringBootTest(properties = {
        "app.security.rate-limits[0].name=register",
        "app.security.rate-limits[0].method=POST",
        "app.security.rate-limits[0].path=/api/auth/register",
        "app.security.rate-limits[0].max-requests=2",
        "app.security.rate-limits[0].window=1h",
        "app.security.rate-limits[1].name=password-reset-request",
        "app.security.rate-limits[1].method=POST",
        "app.security.rate-limits[1].path=/api/auth/password-reset/request",
        "app.security.rate-limits[1].max-requests=2",
        "app.security.rate-limits[1].window=1h",
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private RequestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // Shared singleton across the cached context, and every MockMvc request
        // arrives from the same address, so state must be cleared per test.
        rateLimiter.reset();
    }

    private String registration(String suffix) throws Exception {
        return objectMapper.writeValueAsString(new RegistrationRequest(
                "ratelimit" + suffix, "ratelimit" + suffix + "@example.com", "correct-horse-battery"));
    }

    @Test
    void limitsRegistrationOncePastTheThreshold() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .with(csrf()).contentType(APPLICATION_JSON).content(registration(String.valueOf(i))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType(APPLICATION_JSON).content(registration("overflow")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
    }

    @Test
    void limitsPasswordResetRequests() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetRequest("nobody@example.com"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/password-reset/request")
                            .with(csrf()).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }

        // Unlimited reset requests are a mail-bomb vector against a third party
        // once real delivery exists, and grow the token table meanwhile.
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void countsEachEndpointSeparately() throws Exception {
        // Exhausting one limit must not spill onto another; they are different
        // costs with different thresholds.
        String resetBody = objectMapper.writeValueAsString(
                new com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetRequest("nobody@example.com"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/password-reset/request")
                    .with(csrf()).contentType(APPLICATION_JSON).content(resetBody));
        }

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType(APPLICATION_JSON).content(registration("separate")))
                .andExpect(status().isCreated());
    }

    @Test
    void leavesUnlimitedEndpointsAlone() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        }
    }
}
