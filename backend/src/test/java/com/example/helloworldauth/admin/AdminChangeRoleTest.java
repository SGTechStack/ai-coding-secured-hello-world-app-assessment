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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the admin role-change endpoint (Story 10).
 *
 * <p>Covers: a valid role change is applied and persisted; an invalid role value
 * is rejected with 400 and the role is unchanged; the self-action guard rejects
 * an admin changing their own role (target == principal) and leaves the role
 * unchanged; and CSRF is enforced on the state-changing PATCH.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminChangeRoleTest {

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
        // The target account whose role the admin changes.
        User target = users.save(new User("bob", "bob@example.com",
            passwordEncoder.encode("bob-password-1234+"), Role.USER));
        targetId = target.getId();
    }

    private String body(String role) {
        return "{\"role\":\"%s\"}".formatted(role);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validRoleChangeIsAppliedAndPersisted() throws Exception {
        // Promote bob USER -> ADMIN.
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("bob"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(users.findById(targetId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);

        // Demote bob ADMIN -> USER.
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
        assertThat(users.findById(targetId).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidRoleValueIsRejected() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SUPERUSER")))
            .andExpect(status().isBadRequest());

        // The target's role is unchanged by the rejected request.
        assertThat(users.findById(targetId).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotChangeOwnRole() throws Exception {
        UUID adminId = users.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/users/{id}/role", adminId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("USER")))
            .andExpect(status().isBadRequest());

        // The self-action guard left the admin's own role untouched.
        assertThat(users.findByUsername("admin").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void csrfIsEnforcedOnRoleChange() throws Exception {
        // No .with(csrf()) -> the state-changing PATCH is rejected (403) and the
        // target role is unchanged.
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ADMIN")))
            .andExpect(status().isForbidden());
        assertThat(users.findById(targetId).orElseThrow().getRole()).isEqualTo(Role.USER);
    }
}
