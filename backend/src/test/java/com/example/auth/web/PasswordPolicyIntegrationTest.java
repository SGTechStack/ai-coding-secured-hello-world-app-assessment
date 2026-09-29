package com.example.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.auth.support.EmailTestConfig;
import com.example.auth.support.RecordingEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * HTTP-boundary integration tests for the password policy (IM8-aligned complexity).
 * Verifies that the BACKEND enforces the policy even when the frontend is bypassed.
 * Covers: length, all complexity variants, and that error responses never echo the
 * submitted password (Story 48 / info-disclosure prevention).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(EmailTestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:pwpolicytest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.admin.bootstrap-enabled=false"
})
class PasswordPolicyIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    RecordingEmailService emailService;

    private String csrfToken;

    @BeforeEach
    void fetchCsrfToken() {
        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/auth/csrf", HttpMethod.GET, null, Void.class);
        csrfToken = extractCsrf(resp.getHeaders());
        emailService.reset();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractCsrf(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    return c.substring("XSRF-TOKEN=".length()).split(";")[0];
                }
            }
        }
        return null;
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrfToken);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        return new HttpEntity<>(body, h);
    }

    private ResponseEntity<String> register(String username, String email, String password) {
        return restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                json(String.format(
                        "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        username, email, password)),
                String.class);
    }

    // ── length ────────────────────────────────────────────────────────────────

    @Test
    void password11Chars_isRejected_even_with3Categories() {
        // "Abc-1234567" = 11 chars; upper+lower+special+digit but too short
        ResponseEntity<String> resp = register("u1", "u1@test.com", "Abc-1234567");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void password12Chars_with3Categories_isAccepted() {
        // "Abc-12345678" = 12 chars; upper+lower+digit+special = 4 categories
        ResponseEntity<String> resp = register("u2", "u2@test.com", "Abc-12345678");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void password72Chars_with3Categories_isAccepted() {
        String pw = "a1-" + "a".repeat(69); // 72 chars; lower+digit+special
        assertThat(pw).hasSize(72);
        ResponseEntity<String> resp = register("u3", "u3@test.com", pw);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void password73Chars_isRejected() {
        String pw = "a1-" + "a".repeat(70); // 73 chars
        assertThat(pw).hasSize(73);
        ResponseEntity<String> resp = register("u4", "u4@test.com", pw);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── complexity — too few categories ───────────────────────────────────────

    @Test
    void onlyLowercase_isRejected() {
        ResponseEntity<String> resp = register("u5", "u5@test.com", "alllowercaseee");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void lowercaseAndUppercaseOnly_isRejected() {
        ResponseEntity<String> resp = register("u6", "u6@test.com", "LowerUpperOnly");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void lowercaseAndDigitOnly_isRejected() {
        ResponseEntity<String> resp = register("u7", "u7@test.com", "lowercase12345");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── complexity — valid 3-category combinations ────────────────────────────

    @Test
    void lowercase_uppercase_digit_isAccepted() {
        ResponseEntity<String> resp = register("u8", "u8@test.com", "LowerUpper12345");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void lowercase_digit_special_isAccepted() {
        ResponseEntity<String> resp = register("u9", "u9@test.com", "lowercase123!!!");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void uppercase_digit_special_isAccepted() {
        ResponseEntity<String> resp = register("u10", "u10@test.com", "UPPERCASE123!!!");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void allFourCategories_isAccepted() {
        ResponseEntity<String> resp = register("u11", "u11@test.com", "LowerUpper123!!!");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── secret hygiene — response body must not echo the password ─────────────

    @Test
    void validationErrorResponse_doesNotContainSubmittedPassword() {
        String weakPassword = "myweakpassword"; // lowercase only — 2 categories
        ResponseEntity<String> resp = register("u12", "u12@test.com", weakPassword);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).doesNotContain(weakPassword);
    }

    // ── password-reset confirm uses the same policy ────────────────────────────

    @Test
    void passwordResetConfirm_weakPassword_isRejected() throws Exception {
        // Register + trigger a reset; then try to confirm with a weak new password.
        register("u13", "u13@test.com", "secure-pass-12");
        restTemplate.exchange("/api/auth/password-reset/request", HttpMethod.POST,
                json("{\"email\":\"u13@test.com\"}"), Void.class);
        String token = emailService.lastToken();

        ResponseEntity<String> confirm = restTemplate.exchange(
                "/api/auth/password-reset/confirm", HttpMethod.POST,
                json(String.format("{\"token\":\"%s\",\"newPassword\":\"%s\"}",
                        token, "lowercase12345")), // lowercase + digit only = 2 categories
                String.class);

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void passwordResetConfirm_compliantPassword_isAccepted() throws Exception {
        register("u14", "u14@test.com", "secure-pass-12");
        restTemplate.exchange("/api/auth/password-reset/request", HttpMethod.POST,
                json("{\"email\":\"u14@test.com\"}"), Void.class);
        String token = emailService.lastToken();

        ResponseEntity<String> confirm = restTemplate.exchange(
                "/api/auth/password-reset/confirm", HttpMethod.POST,
                json(String.format("{\"token\":\"%s\",\"newPassword\":\"%s\"}",
                        token, "NewSecure-Pass1")), // lower+upper+special+digit = 4 categories
                String.class);

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void directApiBypass_frontendValidationNotPresent_backendStillRejects() {
        // This test simulates a client that bypasses the frontend and posts directly.
        // The backend must reject the weak password regardless.
        ResponseEntity<String> resp = register("bypass-user", "bypass@test.com", "alllowercaseee");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Correct: 400 returned even without frontend validation running
    }
}
