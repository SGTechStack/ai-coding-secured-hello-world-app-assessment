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
 * Integration tests for the protected greeting endpoint (issue 04). Proves
 * end-to-end authentication: an authenticated Session returns the personalized
 * greeting; no/invalid Session returns 401.
 */
class GreetingControllerIntegrationTest extends HttpIntegrationTest {

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
    void authenticatedSessionReturnsPersonalizedGreeting() {
        AuthTestClient.Session session = http.login("alice", "correcthorsebattery").session();

        ResponseEntity<String> response = http.rest().exchange(
                http.url("/api/hello"), HttpMethod.GET,
                new HttpEntity<>(http.sessionHeaders(session)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, alice");
    }

    @Test
    void noSessionReturnsUnauthorized() {
        try {
            http.rest().getForEntity(http.url("/api/hello"), String.class);
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
            http.rest().exchange(http.url("/api/hello"), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            throw new AssertionError("Expected 401");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
