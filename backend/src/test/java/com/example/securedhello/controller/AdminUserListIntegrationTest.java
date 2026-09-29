package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.AdminBootstrapService;
import com.example.securedhello.support.AuthTestClient;

/**
 * Integration tests for the admin user list and server-side role enforcement
 * (issue 10). An Admin sees the roster (never password hashes); a USER is
 * forbidden; an anonymous caller is unauthorized.
 */
class AdminUserListIntegrationTest extends HttpIntegrationTest {

    private static final String ADMIN_PASSWORD = "change-me-admin-pw";

    private AuthTestClient http;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @BeforeEach
    void setUp() {
        resetClock();
        http = new AuthTestClient(baseUrl());
        userRepository.deleteAll();
        adminBootstrapService.seedIfMissing();
        http.register("bob", "bob@example.com", "correcthorsebattery");
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminSeesUserRosterWithoutPasswordHashes() {
        AuthTestClient.Session admin = http.login("admin", ADMIN_PASSWORD).session();

        ResponseEntity<List> response = http.rest().exchange(http.url("/api/admin/users"),
                HttpMethod.GET, new HttpEntity<>(http.sessionHeaders(admin)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> users = response.getBody();
        assertThat(users).extracting(u -> u.get("username")).contains("admin", "bob");
        for (Map<String, Object> user : users) {
            assertThat(user).containsKeys("username", "email", "role", "enabled", "createdAt");
            assertThat(user).doesNotContainKey("passwordHash");
            assertThat(user).doesNotContainKey("password");
        }
    }

    @Test
    void nonAdminUserIsForbidden() {
        AuthTestClient.Session user = http.login("bob", "correcthorsebattery").session();

        try {
            http.rest().exchange(http.url("/api/admin/users"), HttpMethod.GET,
                    new HttpEntity<>(http.sessionHeaders(user)), List.class);
            throw new AssertionError("Expected 403 for a non-admin user");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void anonymousCallerIsUnauthorized() {
        try {
            http.rest().getForEntity(http.url("/api/admin/users"), List.class);
            throw new AssertionError("Expected 401 for an anonymous caller");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
