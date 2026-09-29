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
 * Integration tests for logout and session invalidation (issue 05). Proves the
 * server-side Session is invalidated and a captured pre-logout cookie is
 * useless afterward.
 */
class LogoutControllerIntegrationTest extends HttpIntegrationTest {

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

    private record Session(String sessionCookie, String csrfCookie, String csrfToken) {
    }

    private Session loginSession() {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String token = (String) csrf.getBody().get("token");
        List<String> csrfCookies = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", token);
        String csrfCookiePair = null;
        for (String c : csrfCookies) {
            String pair = c.split(";", 2)[0];
            headers.add(HttpHeaders.COOKIE, pair);
            if (pair.startsWith("XSRF-TOKEN=")) {
                csrfCookiePair = pair;
            }
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", "alice", "password", "correcthorsebattery"), headers);
        ResponseEntity<Map> login =
                client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
        String sessionCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
        return new Session(sessionCookie, csrfCookiePair, token);
    }

    @Test
    void logoutInvalidatesSessionAndReplayedCookieIsUnauthorized() {
        Session session = loginSession();

        // Sanity: the session works before logout.
        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, session.sessionCookie());
        ResponseEntity<String> before = client.exchange(baseUrl() + "/api/hello",
                HttpMethod.GET, new HttpEntity<>(authed), String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Logout (state-changing -> CSRF token + cookies required).
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, session.sessionCookie());
        logoutHeaders.add(HttpHeaders.COOKIE, session.csrfCookie());
        logoutHeaders.add("X-XSRF-TOKEN", session.csrfToken());
        ResponseEntity<Void> logout = client.exchange(baseUrl() + "/api/logout",
                HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Replay the pre-logout session cookie -> must be rejected.
        try {
            client.exchange(baseUrl() + "/api/hello", HttpMethod.GET,
                    new HttpEntity<>(authed), String.class);
            throw new AssertionError("Expected replayed cookie to be unauthorized");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
