package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Integration tests for Registration (issue 02), exercised over the real HTTP
 * boundary via the shared {@link HttpIntegrationTest} harness. Assertions are
 * on externally observable behaviour (status codes, response bodies) and
 * persisted state (the {@link UserRepository}), never on internals.
 */
class RegistrationControllerIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
    }

    private String registerUrl() {
        return baseUrl() + "/api/register";
    }

    private Map<String, String> body(String username, String email, String password) {
        return Map.of("username", username, "email", email, "password", password);
    }

    @Test
    void validRegistrationCreatesEnabledUserWithBcryptHashAndUserRole() {
        ResponseEntity<Map> response = client.postForEntity(
                registerUrl(), body("alice", "alice@example.com", "correcthorsebattery"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User saved = userRepository.findByUsername("alice").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        // Password is stored as a BCrypt hash, never as plaintext.
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("correcthorsebattery");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getFailedLoginAttempts()).isZero();
    }

    @Test
    void responseNeverContainsPasswordOrHash() {
        ResponseEntity<Map> response = client.postForEntity(
                registerUrl(), body("bob", "bob@example.com", "correcthorsebattery"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContainKey("password");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
    }

    @Test
    void duplicateUsernameIsRejectedAndNoSecondAccountCreated() {
        client.postForEntity(registerUrl(),
                body("carol", "carol@example.com", "correcthorsebattery"), Map.class);

        HttpClientErrorException ex = catchHttpError(() -> client.postForEntity(registerUrl(),
                body("carol", "different@example.com", "correcthorsebattery"), Map.class));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void duplicateEmailIsRejectedAndNoSecondAccountCreated() {
        client.postForEntity(registerUrl(),
                body("dave", "dave@example.com", "correcthorsebattery"), Map.class);

        HttpClientErrorException ex = catchHttpError(() -> client.postForEntity(registerUrl(),
                body("dave2", "dave@example.com", "correcthorsebattery"), Map.class));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void passwordShorterThanTwelveCharactersIsRejectedAndNoAccountCreated() {
        HttpClientErrorException ex = catchHttpError(() -> client.postForEntity(registerUrl(),
                body("erin", "erin@example.com", "short"), Map.class));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByUsername("erin")).isEmpty();
    }

    @Test
    void passwordLongerThanMaxIsRejectedAndNoAccountCreated() {
        String tooLong = "a".repeat(129);

        HttpClientErrorException ex = catchHttpError(() -> client.postForEntity(registerUrl(),
                body("frank", "frank@example.com", tooLong), Map.class));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByUsername("frank")).isEmpty();
    }

    private HttpClientErrorException catchHttpError(Runnable request) {
        try {
            request.run();
            throw new AssertionError("Expected the request to fail with a 4xx error, but it succeeded");
        } catch (HttpClientErrorException ex) {
            return ex;
        }
    }
}
