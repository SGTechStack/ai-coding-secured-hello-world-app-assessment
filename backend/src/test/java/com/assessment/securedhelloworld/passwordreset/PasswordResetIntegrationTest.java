package com.assessment.securedhelloworld.passwordreset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PasswordResetIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger("ROOT")).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger("ROOT")).detachAppender(logAppender);
    }

    private String lastLoggedResetLinkFor(String email) {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(email) && message.contains("token="))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("No reset link logged for " + email));
    }

    private User registerUser(String username, String rawPassword) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private String requestResetPayload(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email));
    }

    private String confirmResetPayload(String token, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword));
    }

    @Test
    void requestReturnsIdenticalGenericResponseRegardlessOfEmailExistence() throws Exception {
        registerUser("reset-generic-user", "correct-horse-battery");

        MvcResult registered = mockMvc.perform(post("/api/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestResetPayload("reset-generic-user@example.com"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult unregistered = mockMvc.perform(post("/api/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestResetPayload("no-such-address@example.com"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(registered.getResponse().getContentAsString())
                .isEqualTo(unregistered.getResponse().getContentAsString());
    }

    @Test
    void confirmSucceedsWithValidTokenAndInvalidatesExistingSessions() throws Exception {
        User user = registerUser("reset-confirm-user", "correct-horse-battery");

        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "reset-confirm-user", "password", "correct-horse-battery")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession preResetSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestResetPayload("reset-confirm-user@example.com"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        String resetLink = lastLoggedResetLinkFor(user.getEmail());
        String token = extractToken(resetLink);

        mockMvc.perform(post("/api/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmResetPayload(token, "brand-new-password123"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findByUsername("reset-confirm-user").orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-password123", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("correct-horse-battery", reloaded.getPasswordHash())).isFalse();

        // the pre-reset session must no longer be authenticated
        mockMvc.perform(get("/api/hello").session(preResetSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmRejectsExpiredToken() throws Exception {
        User user = registerUser("reset-expired-user", "correct-horse-battery");
        PasswordResetToken expiredToken = new PasswordResetToken(
                user.getId(), "expired-token-hash-value", Instant.now().minusSeconds(60));
        tokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmResetPayload("plaintext-for-expired-token-hash-value", "brand-new-password123"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmRejectsAlreadyUsedToken() throws Exception {
        User user = registerUser("reset-reused-user", "correct-horse-battery");

        mockMvc.perform(post("/api/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestResetPayload("reset-reused-user@example.com"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        String resetLink = lastLoggedResetLinkFor(user.getEmail());
        String token = extractToken(resetLink);

        mockMvc.perform(post("/api/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmResetPayload(token, "brand-new-password123"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmResetPayload(token, "yet-another-password123"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }

    private static String extractToken(String resetLink) {
        Matcher matcher = TOKEN_PATTERN.matcher(resetLink);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
