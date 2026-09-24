package com.sgtechstack.helloworldauthapp.passwordreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.auth.RequestRateLimiter;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PasswordResetTest {

    private static final String USERNAME = "resetuser";
    private static final String EMAIL = "resetuser@example.com";
    private static final String OLD_PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "new-correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @Autowired
    private RequestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(USERNAME, EMAIL, passwordEncoder.encode(OLD_PASSWORD), Role.USER, true));
        // Reset requests are now rate-limited per caller, and this class issues
        // several from the same address across its methods. The limiter is a
        // singleton in the cached context, so without this the later methods
        // answer 429 instead of exercising the reset flow. RateLimitFilterTest
        // covers the limiting itself.
        rateLimiter.reset();
    }

    @Test
    void requestReturnsGenericSuccessRegardlessOfWhetherEmailIsRegistered() throws Exception {
        String registeredBody = requestBody(EMAIL);
        String unregisteredBody = requestBody("nobody-here@example.com");

        MvcResult registered = mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(registeredBody))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult unregistered = mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(unregisteredBody))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(registered.getResponse().getContentAsString())
                .isEqualTo(unregistered.getResponse().getContentAsString());
    }

    @Test
    void resetLinkCarriesTheTokenInTheFragmentNotTheQueryString() throws Exception {
        // A fragment is never transmitted to any server, so the token cannot
        // reach the frontend host's access logs or a proxy's. A query parameter
        // could be stripped by the SPA after the fact — and was — but only
        // after the request carrying it had already been logged.
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), linkCaptor.capture());

        assertThat(linkCaptor.getValue())
                .contains("/#token=")
                .doesNotContain("?token=");
    }

    @Test
    void requestForRegisteredEmailIssuesASingleUseTokenHashOnly() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isOk());

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenHash()).isNotBlank();
        assertThat(tokens.get(0).isUsed()).isFalse();
        assertThat(tokens.get(0).getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void confirmWithValidTokenUpdatesPasswordMarksTokenUsedAndInvalidatesSessions() throws Exception {
        // Log in first, to have a live session that reset should kill.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", OLD_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Session works before reset.
        mockMvc.perform(get("/api/hello").session(session)).andExpect(status().isOk());

        String plaintextToken = issueTokenAndCapturePlaintext();

        String confirmBody = confirmBody(plaintextToken, NEW_PASSWORD);
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf()).contentType(APPLICATION_JSON).content(confirmBody))
                .andExpect(status().isOk());

        User reloaded = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isFalse();

        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThat(token.isUsed()).isTrue();

        // The pre-reset session must now be rejected.
        mockMvc.perform(get("/api/hello").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void confirmWithExpiredTokenIsRejectedAndPasswordUnchanged() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        String plaintextToken = "expired-token-plaintext";
        String tokenHash = sha256Base64Url(plaintextToken);
        PasswordResetToken expiredToken =
                new PasswordResetToken(user, tokenHash, Instant.now().minusSeconds(60));
        tokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(confirmBody(plaintextToken, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This reset token has expired"));

        User reloaded = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void confirmWithAlreadyUsedTokenIsRejected() throws Exception {
        String plaintextToken = issueTokenAndCapturePlaintext();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(confirmBody(plaintextToken, NEW_PASSWORD)))
                .andExpect(status().isOk());

        // Second use of the same token must be rejected.
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(confirmBody(plaintextToken, "yet-another-password-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This reset token has already been used"));
    }

    /**
     * The plaintext token only ever exists transiently inside
     * {@code PasswordResetService.requestReset}, passed straight to
     * {@code EmailService.sendPasswordResetEmail} as part of the reset
     * link. Spying on that call is the honest way to recover it in a
     * test, exactly mirroring what a real email would contain, rather
     * than reaching into token internals.
     */
    private String issueTokenAndCapturePlaintext() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf()).contentType(APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), linkCaptor.capture());

        String resetLink = linkCaptor.getValue();
        String rawToken = resetLink.substring(resetLink.indexOf("token=") + "token=".length());
        return URLDecoder.decode(rawToken, StandardCharsets.UTF_8);
    }

    private String requestBody(String email) throws Exception {
        return objectMapper.writeValueAsString(new PasswordResetRequest(email));
    }

    private String confirmBody(String token, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(new PasswordResetConfirmRequest(token, newPassword));
    }

    /**
     * Matches {@code PasswordResetService}'s own hashing exactly, so a
     * token row built directly here (for the expired-token case, which
     * can't otherwise be produced within a test's real-time lifetime)
     * looks up correctly. Duplicated rather than exposing the service's
     * private hashing as a testing seam.
     */
    private static String sha256Base64Url(String value) throws java.security.NoSuchAlgorithmException {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    }
}
