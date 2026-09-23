package com.assessment.securedhelloworld.registration;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private String registrationPayload(String username, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", email,
                "password", password
        ));
    }

    @Test
    void registersNewUserWithBCryptHashedPassword() throws Exception {
        String plaintextPassword = "correct-horse-battery";

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("alice1", "alice1@example.com", plaintextPassword))
                        .with(csrf()))
                .andExpect(status().isCreated());

        User saved = userRepository.findByUsername("alice1").orElseThrow();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getRole().name()).isEqualTo("USER");
        assertThat(saved.getPasswordHash()).isNotEqualTo(plaintextPassword);
        assertThat(passwordEncoder.matches(plaintextPassword, saved.getPasswordHash())).isTrue();

        boolean plaintextLogged = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(plaintextPassword));
        assertThat(plaintextLogged).isFalse();
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("bobby", "bobby-one@example.com", "correct-horse-battery"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("bobby", "bobby-two@example.com", "correct-horse-battery"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        assertThat(userRepository.findByEmail("bobby-two@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("carl-one", "carl@example.com", "correct-horse-battery"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("carl-two", "carl@example.com", "correct-horse-battery"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        assertThat(userRepository.findByUsername("carl-two")).isEmpty();
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("dana", "dana@example.com", "short1"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("dana")).isEmpty();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
