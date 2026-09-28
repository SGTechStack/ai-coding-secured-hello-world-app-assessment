package com.example.helloworldauth.admin;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the admin account-deletion endpoint (Story 11).
 *
 * <p>Covers: a target account is removed and gone from the repository; deleting
 * a user who HAS a password_reset_tokens row succeeds without an FK violation
 * (the child rows are removed first); the self-action guard rejects an admin
 * deleting their OWN account (target == principal) and leaves it present; and
 * CSRF is enforced on the state-changing DELETE.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDeleteUserTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID targetId;

    @BeforeEach
    void seed() {
        resetTokens.deleteAll();
        users.deleteAll();
        // The acting admin — matches @WithMockUser(username="admin").
        users.save(new User("admin", "admin@example.com",
            passwordEncoder.encode("admin-password-123+"), Role.ADMIN));
        // The target account the admin deletes.
        User target = users.save(new User("bob", "bob@example.com",
            passwordEncoder.encode("bob-password-1234+"), Role.USER));
        targetId = target.getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deletesTargetAccount() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", targetId).with(csrf()))
            .andExpect(status().isNoContent());

        // The account is gone from the repository.
        assertThat(users.findById(targetId)).isEmpty();
        assertThat(users.findByUsername("bob")).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deletesUserWithPasswordResetTokenWithoutFkViolation() throws Exception {
        // Seed a password reset token referencing the target (FK on user_id).
        User target = users.findById(targetId).orElseThrow();
        resetTokens.save(new PasswordResetToken(
            target, "hash-" + UUID.randomUUID(),
            Instant.now().plus(1, ChronoUnit.HOURS)));
        assertThat(resetTokens.findByUser(target)).isNotEmpty();

        // Deleting the user succeeds — the child token rows are removed first.
        mockMvc.perform(delete("/api/admin/users/{id}", targetId).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(users.findById(targetId)).isEmpty();
        // The dependent token rows are gone too.
        assertThat(resetTokens.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotDeleteOwnAccount() throws Exception {
        UUID adminId = users.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(delete("/api/admin/users/{id}", adminId).with(csrf()))
            .andExpect(status().isBadRequest());

        // The self-action guard left the admin's own account present.
        assertThat(users.findByUsername("admin")).isPresent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void csrfIsEnforcedOnDelete() throws Exception {
        // No .with(csrf()) -> the state-changing DELETE is rejected (403) and the
        // target account is left present.
        mockMvc.perform(delete("/api/admin/users/{id}", targetId))
            .andExpect(status().isForbidden());
        assertThat(users.findById(targetId)).isPresent();
    }
}
