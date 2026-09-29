package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.entity.PasswordResetToken;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.PasswordResetTokenRepository;
import com.example.securedhello.repository.UserRepository;

/**
 * Integration tests for password-reset request (issue 07). The endpoint is
 * enumeration-resistant (always a generic 200); when the Email matches a User,
 * exactly one live token exists and only its hash is persisted.
 */
class PasswordResetRequestIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @BeforeEach
    void setUp() {
        resetClock();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", "alice", "email", "alice@example.com",
                        "password", "correcthorsebattery"), Map.class);
    }

    private ResponseEntity<Map> requestReset(String email) {
        return client.postForEntity(baseUrl() + "/api/password-reset/request",
                Map.of("email", email), Map.class);
    }

    @Test
    void registeredEmailReturnsGenericSuccessAndStoresOnlyAHashedToken() {
        ResponseEntity<Map> response = requestReset("alice@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User alice = userRepository.findByUsername("alice").orElseThrow();
        List<PasswordResetToken> tokens = tokenRepository.findByUserId(alice.getId());
        assertThat(tokens).hasSize(1);
        PasswordResetToken token = tokens.get(0);
        // Only a hash is stored (64 hex chars for SHA-256), never a plaintext token.
        assertThat(token.getTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getExpiresAt()).isEqualTo(FIXED_NOW.plusSeconds(30 * 60));
    }

    @Test
    void unregisteredEmailReturnsTheSameGenericSuccessAndCreatesNoToken() {
        ResponseEntity<Map> registered = requestReset("alice@example.com");
        ResponseEntity<Map> unknown = requestReset("nobody@example.com");

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getBody()).isEqualTo(registered.getBody());
        assertThat(tokenRepository.findAll()).hasSize(1); // only alice's
    }

    @Test
    void issuingANewTokenInvalidatesAnyExistingUnexpiredTokenForThatUser() {
        requestReset("alice@example.com");
        requestReset("alice@example.com");

        User alice = userRepository.findByUsername("alice").orElseThrow();
        List<PasswordResetToken> live = tokenRepository.findByUserIdAndUsedFalse(alice.getId());
        // At most one live (unused) token per user.
        assertThat(live).hasSize(1);
    }
}
