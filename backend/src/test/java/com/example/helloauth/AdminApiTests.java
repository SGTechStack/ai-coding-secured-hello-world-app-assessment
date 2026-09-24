package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ticket-13 coverage at the HTTP seam: the admin user-management endpoints.
 * Tests drive the real path like the SPA — CSRF bootstrap via
 * {@code GET /api/auth/csrf}, {@code X-XSRF-TOKEN} on every mutation, session
 * cookie from a real login — through the shared {@link ApiTestSupport}
 * fixture. Session invalidation is proven by replaying the target's cookie
 * and getting 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTests extends ApiTestSupport {

    @Autowired
    IpThrottleService ipThrottle;

    @BeforeEach
    void clean() {
        // Tokens first — password_reset_tokens.user_id FKs into users.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // Logins in these tests share the context-scoped throttle's 127.0.0.1.
        ipThrottle.clear();
    }

    // ------------------------------------------------------------------
    // List — contract shape and role enforcement
    // ------------------------------------------------------------------

    @Test
    void adminListsAllUsersWithContractFieldsAndNoHashes() throws Exception {
        User root = seedUser("root", "root@example.com", Role.ADMIN);
        seedUser("alice", "alice@example.com", Role.USER);
        seedUser("bob", "bob@example.com", Role.USER);
        Cookie session = loginSession("root", VALID_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/admin/users").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            // The ratified DTO shape: {id, username, email, role, enabled,
            // createdAt} — and never a password hash.
            .andExpect(jsonPath("$[?(@.username == 'alice')].email")
                .value("alice@example.com"))
            .andExpect(jsonPath("$[?(@.username == 'alice')].role").value("USER"))
            .andExpect(jsonPath("$[?(@.username == 'alice')].enabled").value(true))
            .andExpect(jsonPath("$[?(@.username == 'alice')].id").exists())
            .andExpect(jsonPath("$[?(@.username == 'alice')].createdAt").exists())
            .andExpect(jsonPath("$[?(@.username == 'root')].role").value("ADMIN"))
            .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
            .andReturn();

        // No hash material anywhere in the body — a field renamed but still
        // serialized would slip past doesNotExist on one name alone.
        assertThat(result.getResponse().getContentAsString())
            .doesNotContain(root.getPasswordHash())
            .doesNotContain("$2");
    }

    @Test
    void userRoleGetsForbiddenOnEveryAdminEndpoint() throws Exception {
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        User bob = seedUser("bob", "bob@example.com", Role.USER);
        Cookie session = loginSession("alice", VALID_PASSWORD);

        mockMvc.perform(get("/api/admin/users").cookie(session))
            .andExpect(status().isForbidden());
        patchWithCsrf("/api/admin/users/" + bob.getId() + "/status", session,
                "{\"enabled\":false}")
            .andExpect(status().isForbidden());
        patchWithCsrf("/api/admin/users/" + bob.getId() + "/role", session,
                "{\"role\":\"ADMIN\"}")
            .andExpect(status().isForbidden());
        deleteWithCsrf("/api/admin/users/" + bob.getId(), session)
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousGetsUnauthorizedOnAdminEndpoints() throws Exception {
        // CSRF valid, no session — the chain's entry point must answer 401
        // (never a redirect), matching every other protected endpoint.
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
        Cookie csrf = csrfToken();
        mockMvc.perform(delete("/api/admin/users/1")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Status toggle — enable/disable another account
    // ------------------------------------------------------------------

    @Test
    void statusToggleDisablesAccountKillsSessionsAndBlocksLogin() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);
        Cookie aliceSession = loginSession("alice", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + alice.getId() + "/status",
                adminSession, "{\"enabled\":false}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.enabled").value(false));

        assertThat(userRepository.findByUsername("alice").orElseThrow().isEnabled())
            .isFalse();

        // Disabling is a real suspension: the live session is invalidated
        // (ticket-02 mechanism) and fresh logins get the generic 401.
        mockMvc.perform(get("/api/auth/me").cookie(aliceSession))
            .andExpect(status().isUnauthorized());
        login("alice", VALID_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void statusToggleReEnablesAccount() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        alice.setEnabled(false);
        userRepository.save(alice);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + alice.getId() + "/status",
                adminSession, "{\"enabled\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));

        loginSession("alice", VALID_PASSWORD); // must succeed now
    }

    @Test
    void adminCannotChangeOwnStatus() throws Exception {
        User root = seedUser("root", "root@example.com", Role.ADMIN);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + root.getId() + "/status",
                adminSession, "{\"enabled\":false}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Cannot modify own account"));

        // Even a self-ENABLE is rejected — the guard is uniform, not a
        // disable-only carve-out.
        patchWithCsrf("/api/admin/users/" + root.getId() + "/status",
                adminSession, "{\"enabled\":true}")
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("root").orElseThrow().isEnabled())
            .isTrue();
    }

    // ------------------------------------------------------------------
    // Role change — USER ↔ ADMIN on another account
    // ------------------------------------------------------------------

    @Test
    void roleChangePromotesAndDemotesAnotherAccount() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User bob = seedUser("bob", "bob@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + bob.getId() + "/role",
                adminSession, "{\"role\":\"ADMIN\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));

        // The promotion is effective: a fresh session can reach /api/admin/**.
        Cookie bobSession = loginSession("bob", VALID_PASSWORD);
        mockMvc.perform(get("/api/admin/users").cookie(bobSession))
            .andExpect(status().isOk());

        // Demotion drops the role AND kills the live session — otherwise the
        // stored SecurityContext keeps ROLE_ADMIN until the session expires.
        patchWithCsrf("/api/admin/users/" + bob.getId() + "/role",
                adminSession, "{\"role\":\"USER\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/api/admin/users").cookie(bobSession))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").cookie(bobSession))
            .andExpect(status().isUnauthorized());

        // A fresh session authenticates as plain USER again.
        Cookie bobUserSession = loginSession("bob", VALID_PASSWORD);
        mockMvc.perform(get("/api/admin/users").cookie(bobUserSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        User root = seedUser("root", "root@example.com", Role.ADMIN);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + root.getId() + "/role",
                adminSession, "{\"role\":\"USER\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Cannot modify own account"));

        assertThat(userRepository.findByUsername("root").orElseThrow().getRole())
            .isEqualTo(Role.ADMIN);
    }

    // ------------------------------------------------------------------
    // Delete — removes another account entirely
    // ------------------------------------------------------------------

    @Test
    void deleteRemovesAccountSessionsAndResetTokens() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);
        Cookie aliceSession = loginSession("alice", VALID_PASSWORD);

        // An outstanding reset token FKs into users — delete must not trip
        // the constraint (the token goes with the account).
        com.example.helloauth.passwordreset.PasswordResetToken token =
            new com.example.helloauth.passwordreset.PasswordResetToken();
        token.setUser(alice);
        token.setTokenHash(
            com.example.helloauth.passwordreset.PasswordResetService
                .hashToken("outstanding-token"));
        token.setExpiresAt(Instant.now().plusSeconds(900));
        tokenRepository.save(token);

        deleteWithCsrf("/api/admin/users/" + alice.getId(), adminSession)
            .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("alice")).isEmpty();
        assertThat(tokenRepository.count()).isZero();

        // The deleted account's session is dead and login is impossible.
        mockMvc.perform(get("/api/auth/me").cookie(aliceSession))
            .andExpect(status().isUnauthorized());
        login("alice", VALID_PASSWORD)
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDeleteSelf() throws Exception {
        User root = seedUser("root", "root@example.com", Role.ADMIN);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        deleteWithCsrf("/api/admin/users/" + root.getId(), adminSession)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Cannot modify own account"));

        assertThat(userRepository.findByUsername("root")).isPresent();
    }

    // ------------------------------------------------------------------
    // Edge cases — unknown ids, malformed bodies, CSRF
    // ------------------------------------------------------------------

    @Test
    void mutationsReturnNotFoundForUnknownUserId() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/424242/status", adminSession,
                "{\"enabled\":false}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("User not found"));
        patchWithCsrf("/api/admin/users/424242/role", adminSession,
                "{\"role\":\"ADMIN\"}")
            .andExpect(status().isNotFound());
        deleteWithCsrf("/api/admin/users/424242", adminSession)
            .andExpect(status().isNotFound());
    }

    @Test
    void statusPatchRejectsMissingEnabledFlag() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + alice.getId() + "/status",
                adminSession, "{}")
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("alice").orElseThrow().isEnabled())
            .isTrue();
    }

    @Test
    void rolePatchRejectsRoleOutsideTheEnum() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        // Only USER/ADMIN exist — anything else is a malformed body, never a
        // silent no-op.
        patchWithCsrf("/api/admin/users/" + alice.getId() + "/role",
                adminSession, "{\"role\":\"SUPERUSER\"}")
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("alice").orElseThrow().getRole())
            .isEqualTo(Role.USER);
    }

    @Test
    void adminMutationsRequireCsrfToken() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        Cookie adminSession = loginSession("root", VALID_PASSWORD);

        // Authenticated session but no X-XSRF-TOKEN — the chain rejects
        // before any admin logic runs (MockMvc answers 403 here).
        mockMvc.perform(patch("/api/admin/users/" + alice.getId() + "/status")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/" + alice.getId())
                .cookie(adminSession))
            .andExpect(status().isForbidden());
    }

}
