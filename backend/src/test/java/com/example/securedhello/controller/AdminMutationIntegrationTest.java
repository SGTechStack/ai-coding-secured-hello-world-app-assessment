package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.AdminBootstrapService;
import com.example.securedhello.support.AuthTestClient;

/**
 * Integration tests for admin account mutations and the Admin Self-Action
 * Guard (issue 11): enable/disable, role change, and delete against other
 * accounts succeed; the same actions against the acting admin's own account
 * are rejected (principal id == target id).
 */
class AdminMutationIntegrationTest extends HttpIntegrationTest {

    private static final String ADMIN_PASSWORD = "change-me-admin-pw";

    private AuthTestClient http;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    private long adminId;
    private long bobId;
    private AuthTestClient.Session adminSession;

    @BeforeEach
    void setUp() {
        resetClock();
        http = new AuthTestClient(baseUrl());
        userRepository.deleteAll();
        adminBootstrapService.seedIfMissing();
        http.register("bob", "bob@example.com", "correcthorsebattery");
        adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        bobId = userRepository.findByUsername("bob").orElseThrow().getId();
        adminSession = http.login("admin", ADMIN_PASSWORD).session();
    }

    private ResponseEntity<Map> patch(String path, Map<String, Object> body) {
        return http.rest().exchange(http.url(path), HttpMethod.PATCH,
                new HttpEntity<>(body, http.authedCsrfHeaders(adminSession)), Map.class);
    }

    private HttpClientErrorException patchExpectingFailure(String path, Map<String, Object> body) {
        try {
            patch(path, body);
            throw new AssertionError("Expected the mutation to be rejected");
        } catch (HttpClientErrorException ex) {
            return ex;
        }
    }

    @Test
    void adminCanDisableAnotherAccountAndDisabledUserCannotLogIn() {
        ResponseEntity<Map> response =
                patch("/api/admin/users/" + bobId + "/enabled", Map.of("enabled", false));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByUsername("bob").orElseThrow().isEnabled()).isFalse();

        // Disabled bob cannot log in.
        try {
            http.login("bob", "correcthorsebattery");
            throw new AssertionError("Expected disabled user login to fail");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void loginToDisabledAccountDoesNotCountFailuresOrLockIt() {
        patch("/api/admin/users/" + bobId + "/enabled", Map.of("enabled", false));

        // Several login attempts against the disabled account.
        for (int i = 0; i < 6; i++) {
            try {
                http.login("bob", "correcthorsebattery");
            } catch (HttpClientErrorException ignored) {
                // expected 401
            }
        }

        // A disabled account is refused outright: no failed-attempt increment,
        // no lockout timestamp set.
        User bob = userRepository.findByUsername("bob").orElseThrow();
        assertThat(bob.getFailedLoginAttempts()).isZero();
        assertThat(bob.getLockedUntil()).isNull();
    }

    @Test
    void adminCanChangeAnotherUsersRole() {
        ResponseEntity<Map> response =
                patch("/api/admin/users/" + bobId + "/role", Map.of("role", "ADMIN"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByUsername("bob").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCanDeleteAnotherAccount() {
        ResponseEntity<Void> response = http.rest().exchange(
                http.url("/api/admin/users/" + bobId), HttpMethod.DELETE,
                new HttpEntity<>(http.authedCsrfHeaders(adminSession)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findByUsername("bob")).isEmpty();
    }

    @Test
    void adminCannotDisableDemoteOrDeleteThemselves() {
        HttpClientErrorException disable =
                patchExpectingFailure("/api/admin/users/" + adminId + "/enabled",
                        Map.of("enabled", false));
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        HttpClientErrorException demote =
                patchExpectingFailure("/api/admin/users/" + adminId + "/role",
                        Map.of("role", "USER"));
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        try {
            http.rest().exchange(http.url("/api/admin/users/" + adminId), HttpMethod.DELETE,
                    new HttpEntity<>(http.authedCsrfHeaders(adminSession)), Void.class);
            throw new AssertionError("Expected self-delete to be rejected");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        // The admin account is untouched.
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }
}
