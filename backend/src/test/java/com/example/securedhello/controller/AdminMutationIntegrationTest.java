package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.AdminBootstrapService;

/**
 * Integration tests for admin account mutations and the Admin Self-Action
 * Guard (issue 11): enable/disable, role change, and delete against other
 * accounts succeed; the same actions against the acting admin's own account
 * are rejected (principal id == target id).
 */
class AdminMutationIntegrationTest extends HttpIntegrationTest {

    private static final String ADMIN_PASSWORD = "change-me-admin-pw";

    // JDK HttpClient factory so PATCH is supported (the default factory is not).
    private final RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    private long adminId;
    private long bobId;
    private CsrfSession adminSession;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
        adminBootstrapService.seedIfMissing();
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", "bob", "email", "bob@example.com",
                        "password", "correcthorsebattery"), Map.class);
        adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        bobId = userRepository.findByUsername("bob").orElseThrow().getId();
        adminSession = loginSession("admin", ADMIN_PASSWORD);
    }

    private record CsrfSession(String sessionCookie, String csrfCookie, String csrfToken) {
    }

    private CsrfSession loginSession(String username, String password) {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String token = (String) csrf.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", token);
        String csrfCookie = null;
        for (String c : csrf.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            String pair = c.split(";", 2)[0];
            headers.add(HttpHeaders.COOKIE, pair);
            if (pair.startsWith("XSRF-TOKEN=")) {
                csrfCookie = pair;
            }
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", username, "password", password), headers);
        ResponseEntity<Map> login =
                client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
        String sessionCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
        return new CsrfSession(sessionCookie, csrfCookie, token);
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, adminSession.sessionCookie());
        headers.add(HttpHeaders.COOKIE, adminSession.csrfCookie());
        headers.add("X-XSRF-TOKEN", adminSession.csrfToken());
        return headers;
    }

    private ResponseEntity<Map> patch(String path, Map<String, Object> body) {
        return client.exchange(baseUrl() + path, HttpMethod.PATCH,
                new HttpEntity<>(body, adminHeaders()), Map.class);
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
            loginSession("bob", "correcthorsebattery");
            throw new AssertionError("Expected disabled user login to fail");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
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
        ResponseEntity<Void> response = client.exchange(
                baseUrl() + "/api/admin/users/" + bobId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Void.class);
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
            client.exchange(baseUrl() + "/api/admin/users/" + adminId, HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()), Void.class);
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
