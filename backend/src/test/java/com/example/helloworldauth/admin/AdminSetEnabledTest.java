package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the admin status-toggle endpoint (Story 9).
 *
 * <p>Covers: toggling disables then re-enables a target account (persisted flag);
 * a disabled user can no longer log in (POST /api/login -> 401); the self-action
 * guard rejects an admin toggling their own account and leaves it enabled; and
 * CSRF is enforced on the state-changing PATCH.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSetEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID targetId;

    @BeforeEach
    void seed() {
        users.deleteAll();
        // The acting admin — matches @WithMockUser(username="admin").
        users.save(new User("admin", "admin@example.com",
            passwordEncoder.encode("admin-password-123+"), Role.ADMIN));
        // The target account the admin toggles.
        User target = users.save(new User("bob", "bob@example.com",
            passwordEncoder.encode("bob-password-1234+"), Role.USER));
        targetId = target.getId();
    }

    private String body(boolean enabled) {
        return "{\"enabled\":%s}".formatted(enabled);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void toggleDisablesThenReEnablesTargetAccount() throws Exception {
        // Disable.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("bob"))
            .andExpect(jsonPath("$.enabled").value(false));
        assertThat(users.findById(targetId).orElseThrow().isEnabled()).isFalse();

        // Re-enable.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
        assertThat(users.findById(targetId).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disabledUserCanNoLongerLogIn() throws Exception {
        // Disable bob via the admin endpoint.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(false)))
            .andExpect(status().isOk());

        // bob's correct credentials are now rejected because the account is disabled.
        mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"bob-password-1234+\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotToggleOwnAccount() throws Exception {
        UUID adminId = users.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", adminId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(false)))
            .andExpect(status().isBadRequest());

        // The self-action guard left the admin account untouched.
        assertThat(users.findByUsername("admin").orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void csrfIsEnforcedOnToggle() throws Exception {
        // No .with(csrf()) -> the state-changing PATCH is rejected (403) and the
        // target flag is unchanged.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(false)))
            .andExpect(status().isForbidden());
        assertThat(users.findById(targetId).orElseThrow().isEnabled()).isTrue();
    }
}
