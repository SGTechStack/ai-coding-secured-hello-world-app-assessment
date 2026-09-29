package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.controller.PasswordResetConfirmIntegrationTest.CapturingEmailConfig;
import com.example.securedhello.repository.UserRepository;
import com.example.securedhello.service.EmailService;

/**
 * Integration tests for password-reset confirm and all-session revocation
 * (issue 08). A capturing {@link EmailService} exposes the one-time plaintext
 * token (which is otherwise never returned) so the confirm flow can be driven
 * end-to-end.
 */
@Import(CapturingEmailConfig.class)
class PasswordResetConfirmIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
        capturingEmailService.lastLink.set(null);
        client.postForEntity(baseUrl() + "/api/register",
                Map.of("username", "alice", "email", "alice@example.com",
                        "password", "correcthorsebattery"), Map.class);
    }

    private String requestResetAndCaptureToken() {
        client.postForEntity(baseUrl() + "/api/password-reset/request",
                Map.of("email", "alice@example.com"), Map.class);
        String link = capturingEmailService.lastLink.get();
        assertThat(link).as("stub email must capture a reset link").isNotNull();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private ResponseEntity<Map> confirm(String token, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("token", token, "newPassword", newPassword), headers);
        return client.exchange(baseUrl() + "/api/password-reset/confirm",
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

    /** Logs in and returns the SESSION cookie (name=value). */
    private String loginSessionCookie() {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String csrfToken = (String) csrf.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", csrfToken);
        for (String c : csrf.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", "alice", "password", "correcthorsebattery"), headers);
        ResponseEntity<Map> login =
                client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
        return login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    @Test
    void validTokenUpdatesPasswordMarksTokenUsedAndAllowsNewPassword() {
        String token = requestResetAndCaptureToken();

        ResponseEntity<Map> response = confirm(token, "brandnewpassword1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Old password no longer works, new one does.
        HttpClientErrorException oldPwd = tryLogin("correcthorsebattery");
        assertThat(oldPwd.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> newPwd = successfulLogin("brandnewpassword1");
        assertThat(newPwd.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void allExistingSessionsAreInvalidatedOnSuccessfulReset() {
        String sessionCookie = loginSessionCookie();

        // Session is valid before reset.
        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, sessionCookie);
        assertThat(client.exchange(baseUrl() + "/api/hello", HttpMethod.GET,
                new HttpEntity<>(authed), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String token = requestResetAndCaptureToken();
        assertThat(confirm(token, "brandnewpassword1").getStatusCode()).isEqualTo(HttpStatus.OK);

        // The pre-reset session must now be rejected.
        try {
            client.exchange(baseUrl() + "/api/hello", HttpMethod.GET,
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
        assertThat(successfulLogin("correcthorsebattery").getStatusCode()).isEqualTo(HttpStatus.OK);
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
            successfulLogin(password);
            throw new AssertionError("Expected login to fail");
        } catch (HttpClientErrorException ex) {
            return ex;
        }
    }

    private ResponseEntity<Map> successfulLogin(String password) {
        ResponseEntity<Map> csrf = client.getForEntity(baseUrl() + "/api/csrf", Map.class);
        String csrfToken = (String) csrf.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", csrfToken);
        for (String c : csrf.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", "alice", "password", password), headers);
        return client.exchange(baseUrl() + "/api/login", HttpMethod.POST, entity, Map.class);
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
