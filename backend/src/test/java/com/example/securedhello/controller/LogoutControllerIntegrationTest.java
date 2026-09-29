package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.support.AuthTestClient;

/**
 * Integration tests for logout and session invalidation (issue 05). Proves the
 * server-side Session is invalidated and a captured pre-logout cookie is
 * useless afterward.
 */
class LogoutControllerIntegrationTest extends HttpIntegrationTest {

    private AuthTestClient http;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        resetClock();
        http = new AuthTestClient(baseUrl());
        userRepository.deleteAll();
        http.register("alice", "alice@example.com", "correcthorsebattery");
    }

    @Test
    void logoutInvalidatesSessionAndReplayedCookieIsUnauthorized() {
        AuthTestClient.Session session = http.login("alice", "correcthorsebattery").session();

        // Sanity: the session works before logout.
        HttpHeaders authed = http.sessionHeaders(session);
        ResponseEntity<String> before = http.rest().exchange(http.url("/api/hello"),
                HttpMethod.GET, new HttpEntity<>(authed), String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Logout (state-changing -> CSRF token + cookies required).
        ResponseEntity<Void> logout = http.rest().exchange(http.url("/api/logout"),
                HttpMethod.POST, new HttpEntity<>(http.authedCsrfHeaders(session)), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Replay the pre-logout session cookie -> must be rejected.
        try {
            http.rest().exchange(http.url("/api/hello"), HttpMethod.GET,
                    new HttpEntity<>(authed), String.class);
            throw new AssertionError("Expected replayed cookie to be unauthorized");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
