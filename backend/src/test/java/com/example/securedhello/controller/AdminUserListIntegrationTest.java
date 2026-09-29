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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.AdminBootstrapService;

/**
 * Integration tests for the admin user list and server-side role enforcement
 * (issue 10). An Admin sees the roster (never password hashes); a USER is
 * forbidden; an anonymous caller is unauthorized.
 */
class AdminUserListIntegrationTest extends HttpIntegrationTest {

    private static final String ADMIN_PASSWORD = "change-me-admin-pw";

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
        adminBootstrapService.seedIfMissing();
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", "bob", "email", "bob@example.com",
                        "password", "correcthorsebattery"), Map.class);
    }

    private String loginCookie(String username, String password) {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String token = (String) csrf.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", token);
        for (String c : csrf.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", username, "password", password), headers);
        ResponseEntity<Map> login =
                client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
        return login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminSeesUserRosterWithoutPasswordHashes() {
        String adminCookie = loginCookie("admin", ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, adminCookie);
        ResponseEntity<List> response = client.exchange(baseUrl() + "/api/admin/users",
                HttpMethod.GET, new HttpEntity<>(headers), List.class);

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
        String userCookie = loginCookie("bob", "correcthorsebattery");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, userCookie);
        try {
            client.exchange(baseUrl() + "/api/admin/users", HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);
            throw new AssertionError("Expected 403 for a non-admin user");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void anonymousCallerIsUnauthorized() {
        try {
            client.getForEntity(baseUrl() + "/api/admin/users", List.class);
            throw new AssertionError("Expected 401 for an anonymous caller");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
