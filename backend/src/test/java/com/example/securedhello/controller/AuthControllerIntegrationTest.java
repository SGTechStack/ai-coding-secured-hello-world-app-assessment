package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
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
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.repository.UserRepository;

/**
 * Integration tests for Login + Session + CSRF (issue 03), over the real HTTP
 * boundary with the full Spring Security filter chain and Spring Session over
 * H2. Assertions are on observable behaviour: status codes, cookies, and the
 * persisted failed-attempt counter.
 */
class AuthControllerIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
        register("alice", "alice@example.com", "correcthorsebattery");
    }

    private void register(String username, String email, String password) {
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", username, "email", email, "password", password), Map.class);
    }

    /** Fetches a CSRF token and returns [cookieHeaderValue, csrfToken]. */
    private CsrfHandshake csrfHandshake() {
        ResponseEntity<Map> response =
                client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) response.getBody().get("token");
        assertThat(token).isNotBlank();
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        return new CsrfHandshake(setCookies, token);
    }

    private ResponseEntity<Map> login(String username, String password, CsrfHandshake handshake) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", handshake.token());
        for (String c : handshake.cookies()) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        return client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
    }

    @Test
    void csrfEndpointIsReachableWithoutAuthenticationAndReturnsToken() {
        ResponseEntity<Map> response = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("token")).isNotBlank();
    }

    @Test
    void correctCredentialsCreateSessionCookieAndResetFailedAttempts() {
        // Seed a prior failure so we can prove the counter resets on success.
        CsrfHandshake pre = csrfHandshake();
        try {
            login("alice", "wrongpassword!!", pre);
        } catch (Exception ignored) {
            // wrong-password path throws on 4xx; ignore
        }
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(1);

        CsrfHandshake handshake = csrfHandshake();
        ResponseEntity<Map> response = login("alice", "correcthorsebattery", handshake);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(c -> c.startsWith("SESSION="));
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isZero();
    }

    @Test
    void unknownUsernameAndWrongPasswordReturnIdenticalGenericError() {
        CsrfHandshake h1 = csrfHandshake();
        String wrongPasswordBody = captureLoginErrorBody("alice", "wrongpassword!!", h1);

        CsrfHandshake h2 = csrfHandshake();
        String unknownUserBody = captureLoginErrorBody("nosuchuser", "whatever12345", h2);

        assertThat(wrongPasswordBody).isEqualTo(unknownUserBody);
    }

    @Test
    void eachFailedLoginIncrementsTheFailedAttemptCounter() {
        CsrfHandshake h1 = csrfHandshake();
        captureLoginErrorBody("alice", "wrongpassword!!", h1);
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(1);

        CsrfHandshake h2 = csrfHandshake();
        captureLoginErrorBody("alice", "wrongagain12345", h2);
        assertThat(userRepository.findByUsername("alice").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(2);
    }

    private String captureLoginErrorBody(String username, String password, CsrfHandshake handshake) {
        try {
            login(username, password, handshake);
            throw new AssertionError("Expected login to fail");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            return ex.getResponseBodyAsString();
        }
    }

    private record CsrfHandshake(List<String> cookies, String token) {
    }
}
