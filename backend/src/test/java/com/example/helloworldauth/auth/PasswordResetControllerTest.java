package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.PasswordResetToken;
import com.example.helloworldauth.user.PasswordResetTokenRepository;
import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordResetTokenRepository tokens;
    @Autowired
    private PasswordEncoder encoder;

    @SpyBean
    private EmailService emailService;

    @BeforeEach
    void seed() {
        tokens.deleteAll();
        users.deleteAll();
        users.save(new User("alice", "alice@example.com", encoder.encode("correcthorsebattery"), Role.USER));
    }

    private String body(String email) {
        return """
            {"email":"%s"}""".formatted(email);
    }

    @Test
    void registeredAndUnregisteredEmailsReturnIdenticalResponses() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice@example.com")))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult unregistered = mockMvc.perform(post("/api/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody@example.com")))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(unregistered.getResponse().getContentAsString())
            .isEqualTo(registered.getResponse().getContentAsString());
        assertThat(unregistered.getResponse().getStatus())
            .isEqualTo(registered.getResponse().getStatus());
    }

    @Test
    void registeredEmailPersistsHashedTokenWithFutureExpiryAndInvokesEmailService() throws Exception {
        Instant before = Instant.now();

        mockMvc.perform(post("/api/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice@example.com")))
            .andExpect(status().isOk());

        User alice = users.findByEmail("alice@example.com").orElseThrow();
        List<PasswordResetToken> saved = tokens.findByUser(alice);
        assertThat(saved).hasSize(1);

        PasswordResetToken token = saved.get(0);
        // Stored value is a SHA-256 hex hash (64 hex chars), not the plaintext.
        assertThat(token.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(token.getExpiresAt()).isAfter(before);
        assertThat(token.isUsed()).isFalse();

        // Stub email delivery was invoked for the registered user.
        verify(emailService, times(1)).sendPasswordResetEmail(
            org.mockito.ArgumentMatchers.argThat(u -> u.getEmail().equals("alice@example.com")),
            org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unregisteredEmailPersistsNoTokenAndDoesNotEmail() throws Exception {
        mockMvc.perform(post("/api/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody@example.com")))
            .andExpect(status().isOk());

        assertThat(tokens.findAll()).isEmpty();
        verify(emailService, never()).sendPasswordResetEmail(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice@example.com")))
            .andExpect(status().isForbidden());
    }
}
