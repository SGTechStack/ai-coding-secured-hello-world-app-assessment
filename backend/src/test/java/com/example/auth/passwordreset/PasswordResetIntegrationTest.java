package com.example.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.auth.support.EmailTestConfig;
import com.example.auth.support.RecordingEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * HTTP-boundary integration tests for password reset (Stories 18–24, 46, 48, 52).
 * Real Spring Security filter chain, CSRF, Spring Session JDBC, H2.
 * Clock is mocked for deterministic expiry; EmailService replaced with a
 * recording double to capture the plaintext token.
 * Rate limit set high so these functional tests never trip the 429 path
 * (that path is covered by ResetRequestRateLimitTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(EmailTestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:resettest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.reset.token-ttl-minutes=30",
    "app.reset.request-rate-limit.max-attempts=1000",
    "app.frontend.base-url=http://localhost:3000",
    "app.admin.bootstrap-enabled=false"   // Clock is mocked; skip startup seeder
})
class PasswordResetIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    RecordingEmailService emailService;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    ResetTokenService resetTokenService;

    @Autowired
    ResetRequestRateLimiter rateLimiter;

    @MockBean
    Clock clock;

    @BeforeEach
    void setup() {
        Mockito.when(clock.instant()).thenReturn(BASE);
        Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        emailService.reset();
        rateLimiter.clearForTesting();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractCsrfCookie(HttpHeaders headers) {
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

    private String extractSessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("SESSION=")) {
                    return c.split(";")[0];
                }
            }
        }
        return null;
    }

    private String freshCsrf() {
        return extractCsrfCookie(
                restTemplate.exchange("/api/auth/csrf", HttpMethod.GET, null, Void.class).getHeaders());
    }

    private HttpEntity<String> json(String body) {
        String csrf = freshCsrf();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrf);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<String> jsonWithExtraHeader(String body, String headerName, String headerValue) {
        String csrf = freshCsrf();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrf);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        h.set(headerName, headerValue);
        return new HttpEntity<>(body, h);
    }

    private void register(String username, String email) {
        restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                json(String.format("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"secure-pass-12\"}",
                        username, email)),
                Void.class);
    }

    private ResponseEntity<String> requestReset(String email) {
        return restTemplate.exchange("/api/auth/password-reset/request", HttpMethod.POST,
                json(String.format("{\"email\":\"%s\"}", email)), String.class);
    }

    private ResponseEntity<String> confirmReset(String token, String newPassword) {
        return restTemplate.exchange("/api/auth/password-reset/confirm", HttpMethod.POST,
                json(String.format("{\"token\":\"%s\",\"newPassword\":\"%s\"}", token, newPassword)), String.class);
    }

    private ResponseEntity<String> login(String username, String password) {
        return restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                json(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)), String.class);
    }

    // ── request ────────────────────────────────────────────────────────────────

    @Test
    void requestForExistingEmail_returns200AndSendsLinkWithToken() {
        register("alice", "alice@reset.test");

        ResponseEntity<String> response = requestReset("alice@reset.test");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emailService.sentCount()).isEqualTo(1);
        assertThat(emailService.lastToken()).isNotBlank();
    }

    @Test
    void requestForUnknownEmail_returns200IdenticalAndSendsNoEmail() {
        // Story 18: enumeration resistance — unknown email yields identical response, no email sent
        register("bob", "bob@reset.test");
        ResponseEntity<String> existing = requestReset("bob@reset.test");
        emailService.reset();

        ResponseEntity<String> unknown = requestReset("nobody@reset.test");

        assertThat(unknown.getStatusCode()).isEqualTo(existing.getStatusCode());
        assertThat(unknown.getBody()).isEqualTo(existing.getBody());
        assertThat(emailService.sentCount()).isZero(); // no email for unknown address
    }

    @Test
    void storedToken_isHashedNotPlaintext() {
        // Story 46 / never persist plaintext
        register("carol", "carol@reset.test");
        requestReset("carol@reset.test");

        String plaintext = emailService.lastToken();
        // Look up by the expected hash (repo may hold tokens from earlier tests in this class).
        PasswordResetToken stored = tokenRepository.findByTokenHash(resetTokenService.hash(plaintext))
                .orElseThrow(() -> new AssertionError("token not stored by its SHA-256 hash"));

        assertThat(stored.getTokenHash()).isNotEqualTo(plaintext);
        // hash is SHA-256 hex → 64 hex chars
        assertThat(stored.getTokenHash()).matches("[0-9a-f]{64}");
        // No stored row should ever contain the plaintext token
        assertThat(tokenRepository.findByTokenHash(plaintext)).isEmpty();
    }

    @Test
    void tokensAreUniqueAcrossRequests() {
        // Story 46: high-entropy, unique per request
        register("dave", "dave@reset.test");
        requestReset("dave@reset.test");
        String first = emailService.lastToken();
        requestReset("dave@reset.test");
        String second = emailService.lastToken();

        assertThat(first).isNotEqualTo(second);
        // Base64URL of 32 bytes without padding → 43 chars
        assertThat(first).hasSize(43);
    }

    @Test
    void resetLink_usesConfiguredBaseUrlNotHostHeader() {
        // Story 52: Host-header injection must not poison the reset link
        register("erin", "erin@reset.test");

        restTemplate.exchange("/api/auth/password-reset/request", HttpMethod.POST,
                jsonWithExtraHeader("{\"email\":\"erin@reset.test\"}", "Host", "attacker.example.com"),
                String.class);

        assertThat(emailService.lastResetLink()).startsWith("http://localhost:3000/reset-password?token=");
        assertThat(emailService.lastResetLink()).doesNotContain("attacker.example.com");
    }

    // ── confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirmWithValidToken_changesPassword() {
        // Story 20
        register("frank", "frank@reset.test");
        requestReset("frank@reset.test");
        String token = emailService.lastToken();

        ResponseEntity<String> confirm = confirmReset(token, "brand-new-pass-34");
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Old password fails, new password works
        assertThat(login("frank", "secure-pass-12").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("frank", "brand-new-pass-34").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirm_doesNotAutoAuthenticate() {
        // Story 24: confirm must not log the user in
        register("grace", "grace@reset.test");
        requestReset("grace@reset.test");
        String token = emailService.lastToken();

        ResponseEntity<String> confirm = confirmReset(token, "brand-new-pass-34");

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractSessionCookie(confirm.getHeaders())).isNull();
    }

    @Test
    void confirmInvalidatesAllExistingSessions() {
        // Story 7 / 21: reset kills all live sessions
        register("heidi", "heidi@reset.test");
        ResponseEntity<String> loginResp = login("heidi", "secure-pass-12");
        String sessionCookie = extractSessionCookie(loginResp.getHeaders());
        assertThat(sessionCookie).isNotNull();

        // Verify the session works before reset
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.COOKIE, sessionCookie);
        assertThat(restTemplate.exchange("/api/hello", HttpMethod.GET, new HttpEntity<>(h), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        requestReset("heidi@reset.test");
        confirmReset(emailService.lastToken(), "brand-new-pass-34");

        // The pre-reset session cookie must now be rejected
        assertThat(restTemplate.exchange("/api/hello", HttpMethod.GET, new HttpEntity<>(h), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredToken_returns400AndDoesNotChangePassword() {
        // Story 22
        register("ivan", "ivan@reset.test");
        requestReset("ivan@reset.test");
        String token = emailService.lastToken();

        // Advance clock past the 30-minute TTL
        Mockito.when(clock.instant()).thenReturn(BASE.plusSeconds(31 * 60));

        ResponseEntity<String> confirm = confirmReset(token, "brand-new-pass-34");
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Reset clock; old password still works, new one does not
        Mockito.when(clock.instant()).thenReturn(BASE);
        assertThat(login("ivan", "secure-pass-12").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void usedToken_cannotBeReused() {
        // Story 23: single-use
        register("judy", "judy@reset.test");
        requestReset("judy@reset.test");
        String token = emailService.lastToken();

        assertThat(confirmReset(token, "brand-new-pass-34").getStatusCode()).isEqualTo(HttpStatus.OK);
        // second use of the same token
        assertThat(confirmReset(token, "another-new-pass-56").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownToken_returns400() {
        ResponseEntity<String> confirm = confirmReset("this-token-does-not-exist", "brand-new-pass-34");
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmWithWeakPassword_returns400() {
        // password policy still enforced on the new password
        register("karl", "karl@reset.test");
        requestReset("karl@reset.test");
        String token = emailService.lastToken();

        assertThat(confirmReset(token, "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── secret hygiene (Story 48) ─────────────────────────────────────────────

    @Test
    void responses_doNotLeakTokenOrHash() {
        register("laura", "laura@reset.test");
        ResponseEntity<String> requestResp = requestReset("laura@reset.test");
        String token = emailService.lastToken();
        String hash = resetTokenService.hash(token);

        // The generic request response must not contain the token or its hash
        assertThat(requestResp.getBody()).doesNotContain(token, hash);

        ResponseEntity<String> confirmResp = confirmReset(token, "brand-new-pass-34");
        assertThat(confirmResp.getBody()).doesNotContain(token, hash);
    }
}
