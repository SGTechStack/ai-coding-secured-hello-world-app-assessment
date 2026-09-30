package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.IpThrottleService;

/**
 * Integration tests for Account Lockout and IP Throttling (issue 06), over the
 * real HTTP boundary. Lockout is per-account (locked_until); throttling is
 * per-IP and independent. Time-dependent behaviour is advanced via the shared
 * test {@link com.example.securedhello.HttpIntegrationTest} clock.
 */
class LockoutIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IpThrottleService ipThrottleService;

    @BeforeEach
    void setUp() {
        resetClock();
        ipThrottleService.reset();
        userRepository.deleteAll();
        register("alice", "alice@example.com", "correcthorsebattery");
    }

    private HttpHeaders csrfHeaders() {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String token = (String) csrf.getBody().get("token");
        List<String> csrfCookies = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", token);
        for (String c : csrfCookies) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        return headers;
    }

    private void register(String username, String email, String password) {
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "email", email, "password", password), csrfHeaders());
        client.exchange(baseUrl() + "/api/register", HttpMethod.POST, entity, Map.class);
    }

    private ResponseEntity<Map> login(String username, String password) {
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), csrfHeaders());
        return client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
    }

    private HttpStatusCodeException loginExpectingFailure(String username, String password) {
        try {
            login(username, password);
            throw new AssertionError("Expected login to fail");
        } catch (HttpStatusCodeException ex) {
            return ex;
        }
    }

    @Test
    void fiveConsecutiveFailuresLockTheAccount() {
        for (int i = 0; i < 5; i++) {
            loginExpectingFailure("alice", "wrongpassword" + i);
        }
        assertThat(userRepository.findByUsername("alice").orElseThrow().getLockedUntil())
                .isNotNull();
    }

    @Test
    void lockedAccountRejectsCorrectCredentialsUntilCooldownElapses() {
        for (int i = 0; i < 5; i++) {
            loginExpectingFailure("alice", "wrongpassword" + i);
        }

        // Correct password, but still locked -> rejected with 401.
        HttpClientErrorException stillLocked =
                (HttpClientErrorException) loginExpectingFailure("alice", "correcthorsebattery");
        assertThat(stillLocked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // After the cooldown, the correct password succeeds and resets the counter.
        advanceTime(Duration.ofMinutes(16));
        ResponseEntity<Map> success = login("alice", "correcthorsebattery");
        assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByUsername("alice").orElseThrow().getFailedLoginAttempts())
                .isZero();
        assertThat(userRepository.findByUsername("alice").orElseThrow().getLockedUntil())
                .isNull();
    }

    @Test
    void ipThrottlingEngagesIndependentlyOfAnySingleAccountAcrossManyUsernames() {
        // Fail against many DISTINCT usernames from the same IP so account
        // lockout never trips for any one account, yet the IP is throttled.
        for (int i = 0; i < 25; i++) {
            register("victim" + i, "victim" + i + "@example.com", "correcthorsebattery");
        }
        HttpStatusCodeException throttled = null;
        for (int i = 0; i < 25; i++) {
            HttpStatusCodeException ex = loginExpectingFailure("victim" + i, "wrongpassword");
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throttled = ex;
                break;
            }
        }
        assertThat(throttled)
                .as("expected the IP to be throttled (429) after many failures across usernames")
                .isNotNull();
    }
}
