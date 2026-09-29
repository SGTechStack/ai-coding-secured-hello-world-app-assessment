package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.support.AuthTestClient;

/**
 * Integration tests for Login + Session + CSRF (issue 03), over the real HTTP
 * boundary with the full Spring Security filter chain and Spring Session over
 * H2. Assertions are on observable behaviour: status codes, cookies, and the
 * persisted failed-attempt counter.
 */
class AuthControllerIntegrationTest extends HttpIntegrationTest {

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
    void csrfEndpointIsReachableWithoutAuthenticationAndReturnsToken() {
        AuthTestClient.Csrf csrf = http.csrf();
        assertThat(csrf.token()).isNotBlank();
    }

    @Test
    void correctCredentialsCreateSessionCookieAndResetFailedAttempts() {
        // Seed a prior failure so we can prove the counter resets on success.
        try {
            http.login("alice", "wrongpassword!!");
        } catch (HttpClientErrorException ignored) {
            // wrong-password path throws on 4xx; ignore
        }
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(1);

        ResponseEntity<Map> response = http.login("alice", "correcthorsebattery").response();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(c -> c.startsWith("SESSION="));
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isZero();
    }

    @Test
    void unknownUsernameAndWrongPasswordReturnIdenticalGenericError() {
        String wrongPasswordBody = captureLoginErrorBody("alice", "wrongpassword!!");
        String unknownUserBody = captureLoginErrorBody("nosuchuser", "whatever12345");

        assertThat(wrongPasswordBody).isEqualTo(unknownUserBody);
    }

    @Test
    void eachFailedLoginIncrementsTheFailedAttemptCounter() {
        captureLoginErrorBody("alice", "wrongpassword!!");
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(1);

        captureLoginErrorBody("alice", "wrongagain12345");
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(2);
    }

    private String captureLoginErrorBody(String username, String password) {
        try {
            http.login(username, password);
            throw new AssertionError("Expected login to fail");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            return ex.getResponseBodyAsString();
        }
    }
}
