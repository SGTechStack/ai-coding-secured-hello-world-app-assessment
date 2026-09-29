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

/**
 * Integration tests for the protected greeting endpoint (issue 04). Proves
 * end-to-end authentication: an authenticated Session returns the personalized
 * greeting; no/invalid Session returns 401.
 */
class GreetingControllerIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", "alice", "email", "alice@example.com",
                        "password", "correcthorsebattery"), Map.class);
    }

    /** Logs in and returns the SESSION cookie header value (name=value). */
    private String loginAndGetSessionCookie() {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String token = (String) csrf.getBody().get("token");
        List<String> csrfCookies = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", token);
        for (String c : csrfCookies) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", "alice", "password", "correcthorsebattery"), headers);
        ResponseEntity<Map> login =
                client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);

        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SESSION cookie set on login"));
    }

    @Test
    void authenticatedSessionReturnsPersonalizedGreeting() {
        String sessionCookie = loginAndGetSessionCookie();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<String> response = client.exchange(
                baseUrl() + "/api/hello", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, alice");
    }

    @Test
    void noSessionReturnsUnauthorized() {
        try {
            client.getForEntity(baseUrl() + "/api/hello", String.class);
            throw new AssertionError("Expected 401");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void invalidSessionCookieReturnsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION=not-a-real-session-id");
        try {
            client.exchange(baseUrl() + "/api/hello", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            throw new AssertionError("Expected 401");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
