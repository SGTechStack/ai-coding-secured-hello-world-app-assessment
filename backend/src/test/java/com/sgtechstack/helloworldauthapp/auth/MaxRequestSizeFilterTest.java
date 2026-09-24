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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Oversized bodies must be refused before anything buffers or parses them.
 *
 * <p>Bean validation cannot serve this purpose: {@code @Size} constraints run
 * after Jackson has already materialised the entire payload, so a huge body is
 * fully read before being rejected. Tomcat's
 * {@code max-http-form-post-size} does not help either, because every endpoint
 * here except login takes JSON, for which Spring Boot applies no default cap.
 */
@SpringBootTest(properties = "app.security.max-request-body-size=1KB")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MaxRequestSizeFilterTest {

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
        rateLimiter.reset();
    }

    @Test
    void rejectsABodyOverTheLimitWithoutParsingIt() throws Exception {
        String oversizedPassword = "x".repeat(4096);
        String body = objectMapper.writeValueAsString(new RegistrationRequest(
                "sizeuser", "sizeuser@example.com", oversizedPassword));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Request body too large."));
    }

    @Test
    void allowsANormalSizedBodyThrough() throws Exception {
        String body = objectMapper.writeValueAsString(new RegistrationRequest(
                "normalsize", "normalsize@example.com", "correct-horse-battery"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void appliesBeforeAuthenticationSoItProtectsUnauthenticatedEndpoints() throws Exception {
        // The endpoints that most need this are the ones reachable without a
        // session, since those are the ones an anonymous caller can hammer.
        String body = "{\"email\":\"" + "x".repeat(4096) + "@example.com\"}";

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge());
    }
}
