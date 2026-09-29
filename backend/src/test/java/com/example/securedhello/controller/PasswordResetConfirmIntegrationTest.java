package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.controller.PasswordResetConfirmIntegrationTest.CapturingEmailConfig;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.EmailService;
import com.example.securedhello.support.AuthTestClient;

/**
 * Integration tests for password-reset confirm and all-session revocation
 * (issue 08). A capturing {@link EmailService} exposes the one-time plaintext
 * token (which is otherwise never returned) so the confirm flow can be driven
 * end-to-end. Confirm is CSRF-protected, so requests carry a CSRF token.
 */
@Import(CapturingEmailConfig.class)
class PasswordResetConfirmIntegrationTest extends HttpIntegrationTest {

    private AuthTestClient http;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void setUp() {
        resetClock();
        http = new AuthTestClient(baseUrl());
        userRepository.deleteAll();
        capturingEmailService.lastLink.set(null);
        http.register("alice", "alice@example.com", "correcthorsebattery");
    }

    private String requestResetAndCaptureToken() {
        // Reset request is public and CSRF-exempt.
        http.rest().postForEntity(http.url("/api/password-reset/request"),
                Map.of("email", "alice@example.com"), Map.class);
        String link = capturingEmailService.lastLink.get();
        assertThat(link).as("stub email must capture a reset link").isNotNull();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> confirm(String token, String newPassword) {
        // Confirm is CSRF-protected: obtain a token and echo it.
        AuthTestClient.Csrf csrf = http.csrf();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", csrf.token());
        headers.add(HttpHeaders.COOKIE, csrf.cookie());
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("token", token, "newPassword", newPassword), headers);
        return http.rest().exchange(http.url("/api/password-reset/confirm"),
                HttpMethod.POST, entity, Map.class);
    }

    private HttpClientErrorException confirmExpectingFailure(String token, String newPassword) {
        try {
            confirm(token, newPassword);
            throw new AssertionError("Expected confirm to fail");
        } catch (HttpClientErrorException ex) {
            return ex;
        }
    }

    @Test
    void validTokenUpdatesPasswordMarksTokenUsedAndAllowsNewPassword() {
        String token = requestResetAndCaptureToken();

        ResponseEntity<Map> response = confirm(token, "brandnewpassword1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Old password no longer works, new one does.
        HttpClientErrorException oldPwd = tryLogin("correcthorsebattery");
        assertThat(oldPwd.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.login("alice", "brandnewpassword1").response().getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void allExistingSessionsAreInvalidatedOnSuccessfulReset() {
        // Log in against the real (jdbc) session store and confirm the session works.
        AuthTestClient.Session session = http.login("alice", "correcthorsebattery").session();
        HttpHeaders authed = http.sessionHeaders(session);
        assertThat(http.rest().exchange(http.url("/api/hello"), HttpMethod.GET,
                new HttpEntity<>(authed), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String token = requestResetAndCaptureToken();
        assertThat(confirm(token, "brandnewpassword1").getStatusCode()).isEqualTo(HttpStatus.OK);

        // The pre-reset session must now be rejected under the real session store.
        try {
            http.rest().exchange(http.url("/api/hello"), HttpMethod.GET,
                    new HttpEntity<>(authed), String.class);
            throw new AssertionError("Expected pre-reset session to be revoked");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void expiredTokenIsRejectedAndPasswordUnchanged() {
        String token = requestResetAndCaptureToken();
        advanceTime(Duration.ofMinutes(31));

        HttpClientErrorException ex = confirmExpectingFailure(token, "brandnewpassword1");
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Original password still works.
        assertThat(http.login("alice", "correcthorsebattery").response().getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void alreadyUsedTokenIsRejectedOnReuse() {
        String token = requestResetAndCaptureToken();
        assertThat(confirm(token, "brandnewpassword1").getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpClientErrorException ex = confirmExpectingFailure(token, "anotherpassword12");
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpClientErrorException tryLogin(String password) {
        try {
            http.login("alice", password);
            throw new AssertionError("Expected login to fail");
        } catch (HttpClientErrorException ex) {
            return ex;
        }
    }

    /** Test-only EmailService that records the last reset link. */
    static class CapturingEmailService extends EmailService {
        final AtomicReference<String> lastLink = new AtomicReference<>();

        @Override
        public void sendPasswordResetEmail(String email, String resetLink) {
            lastLink.set(resetLink);
        }
    }

    @TestConfiguration
    static class CapturingEmailConfig {
        @Bean
        @Primary
        CapturingEmailService capturingEmailService() {
            return new CapturingEmailService();
        }
    }
}
