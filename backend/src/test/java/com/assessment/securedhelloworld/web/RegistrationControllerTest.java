package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registersAccountWithValidDetails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.existsByUsername("alice")).isTrue();
        var user = userRepository.findByUsername("alice").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("correct-horse-battery");
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getRole().name()).isEqualTo("USER");
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "bob",
                "email", "bob@example.com",
                "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated());

        String secondBody = objectMapper.writeValueAsString(Map.of(
                "username", "bob",
                "email", "bob2@example.com",
                "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(secondBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "carol",
                "email", "carol@example.com",
                "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated());

        String secondBody = objectMapper.writeValueAsString(Map.of(
                "username", "carol2",
                "email", "carol@example.com",
                "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(secondBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "dave",
                "email", "dave@example.com",
                "password", "short1"));
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WEAK_PASSWORD"));

        assertThat(userRepository.existsByUsername("dave")).isFalse();
    }

    @Test
    void rejectsRegistrationWithoutCsrfToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "erin",
                "email", "erin@example.com",
                "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }
}
