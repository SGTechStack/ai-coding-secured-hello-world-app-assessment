package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private String body(String username, String email, String password) {
        return """
            {"username":"%s","email":"%s","password":"%s"}
            """.formatted(username, email, password);
    }

    @Test
    void registersValidAccount() throws Exception {
        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice", "alice@example.com", "correcthorsebattery")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("USER"));

        var saved = users.findByUsername("alice").orElseThrow();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).doesNotContain("correcthorsebattery");
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("bob", "bob@example.com", "correcthorsebattery")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("bob", "bob2@example.com", "correcthorsebattery")))
            .andExpect(status().isConflict());

        assertThat(users.existsByEmail("bob2@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("carol", "dup@example.com", "correcthorsebattery")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("carol2", "dup@example.com", "correcthorsebattery")))
            .andExpect(status().isConflict());

        assertThat(users.existsByUsername("carol2")).isFalse();
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/api/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("dave", "dave@example.com", "short")))
            .andExpect(status().isBadRequest());

        assertThat(users.existsByUsername("dave")).isFalse();
    }

    @Test
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("erin", "erin@example.com", "correcthorsebattery")))
            .andExpect(status().isForbidden());
    }
}
