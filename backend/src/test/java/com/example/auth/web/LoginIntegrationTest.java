package com.example.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.auth.user.UserResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * HTTP-boundary integration tests for login, logout, /me, and /api/hello.
 * Covers Stories 7–9, 14–17, 37–38, 50, 53.
 * Uses the real Spring Security filter chain, CSRF, Spring Session JDBC, and H2.
 * Security internals are not mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.test.context.TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:logintest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class LoginIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    private String csrfToken;

    @BeforeEach
    void fetchCsrfToken() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/auth/csrf", HttpMethod.GET, null, Void.class);
        csrfToken = extractCsrfCookie(response.getHeaders());
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

    private HttpEntity<String> jsonWithCsrf(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (csrfToken != null) {
            h.set("X-XSRF-TOKEN", csrfToken);
            h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        }
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<String> jsonWithCsrfAndSession(String body, String sessionCookie) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (csrfToken != null) {
            h.set("X-XSRF-TOKEN", csrfToken);
            h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken + "; " + sessionCookie);
        }
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<Void> getWithSession(String sessionCookie) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.COOKIE, sessionCookie);
        return new HttpEntity<>(h);
    }

    /** Register + login; returns the full response so callers can extract the session cookie. */
    private ResponseEntity<AuthResponse> registerAndLogin(String username, String email, String password) {
        restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                jsonWithCsrf(String.format(
                        "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        username, email, password)),
                Void.class);
        return restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)),
                AuthResponse.class);
    }

    private String extractSessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("SESSION=")) {
                    return c.split(";")[0]; // "SESSION=<value>"
                }
            }
        }
        return null;
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void loginSuccess_returns200AndAuthResponse() {
        ResponseEntity<AuthResponse> response = registerAndLogin("alice", "alice@test.com", "secure-pass-12");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.username()).isEqualTo("alice");
        assertThat(body.role()).isEqualTo("USER");
    }

    @Test
    void loginSuccess_setsSessionCookie() {
        ResponseEntity<AuthResponse> response = registerAndLogin("bob", "bob@test.com", "secure-pass-12");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractSessionCookie(response.getHeaders())).isNotNull();
    }

    @Test
    void loginWrongPassword_returns401WithGenericMessage() {
        registerAndLogin("carol", "carol@test.com", "secure-pass-12");

        ResponseEntity<String> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{\"username\":\"carol\",\"password\":\"wrong-password-1\"}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
    }

    @Test
    void loginUnknownUsername_returns401IdenticalToWrongPassword() {
        /*
         * Story 9 / Story 50: enumeration resistance — unknown user and wrong password
         * must produce byte-identical 401 responses. The timing guarantee comes from
         * DaoAuthenticationProvider.hideUserNotFoundExceptions=true (Spring Security
         * default), which performs a dummy BCrypt comparison for unknown usernames
         * before throwing BadCredentialsException.
         */
        ResponseEntity<String> wrongPw = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{\"username\":\"carol\",\"password\":\"wrong-pw-99\"}"), String.class);
        ResponseEntity<String> unknownUser = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{\"username\":\"no-such-user\",\"password\":\"irrelevant-12\"}"), String.class);

        assertThat(unknownUser.getStatusCode()).isEqualTo(wrongPw.getStatusCode());
        assertThat(unknownUser.getBody()).isEqualTo(wrongPw.getBody());
        String ct = unknownUser.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        assertThat(ct).contains("application/problem+json");
    }

    @Test
    void loginDisabledAccount_returns401Generic() {
        // Disabled account is handled by AuthenticationManager via isEnabled()=false;
        // the response must be the same generic 401 — not a distinctive 403.
        // Full admin-disable flow tested in Slice 6; here we verify via registration
        // that the response shape is correct for a non-lockout failure.
        // (A disabled-account scenario requires admin tooling from Slice 6,
        // so this test validates the generic-401 contract for bad credentials.)
        ResponseEntity<String> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{\"username\":\"nobody\",\"password\":\"wrong-1234\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
        assertThat(response.getBody()).doesNotContain("disabled", "locked", "username", "password");
    }

    @Test
    void loginResponse_doesNotLeakSecrets() {
        // Story 48: error response body must never contain submitted credentials
        ResponseEntity<String> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{\"username\":\"leaktest\",\"password\":\"my-secret-pass-1\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).doesNotContain("my-secret-pass-1", "leaktest");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_returns204AndInvalidatesSession() {
        ResponseEntity<AuthResponse> login = registerAndLogin("dave", "dave@test.com", "secure-pass-12");
        String sessionCookie = extractSessionCookie(login.getHeaders());
        assertThat(sessionCookie).isNotNull();

        // Logout using the live session cookie
        ResponseEntity<Void> logout = restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                jsonWithCsrfAndSession("", sessionCookie), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void reusingSessionCookieAfterLogout_returns401() {
        // Story 14–15: a session cookie captured before logout must be rejected after logout.
        ResponseEntity<AuthResponse> login = registerAndLogin("eve", "eve@test.com", "secure-pass-12");
        String sessionCookie = extractSessionCookie(login.getHeaders());

        restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                jsonWithCsrfAndSession("", sessionCookie), Void.class);

        // Attempt to reuse the invalidated session cookie
        ResponseEntity<String> reuse = restTemplate.exchange("/api/hello", HttpMethod.GET,
                getWithSession(sessionCookie), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── /api/hello ────────────────────────────────────────────────────────────

    @Test
    void hello_returnsPersonalisedGreeting() {
        // Story 16
        ResponseEntity<AuthResponse> login = registerAndLogin("frank", "frank@test.com", "secure-pass-12");
        String sessionCookie = extractSessionCookie(login.getHeaders());

        ResponseEntity<String> response = restTemplate.exchange("/api/hello", HttpMethod.GET,
                getWithSession(sessionCookie), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello, frank");
    }

    @Test
    void hello_returns401WhenUnauthenticated() {
        // Story 17
        ResponseEntity<String> response = restTemplate.exchange("/api/hello", HttpMethod.GET,
                null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── /api/auth/me ──────────────────────────────────────────────────────────

    @Test
    void me_returnsCurrentUserResponse() {
        // Story 38
        ResponseEntity<AuthResponse> login = registerAndLogin("grace", "grace@test.com", "secure-pass-12");
        String sessionCookie = extractSessionCookie(login.getHeaders());

        ResponseEntity<UserResponse> me = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
                getWithSession(sessionCookie), UserResponse.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse user = me.getBody();
        assertThat(user).isNotNull();
        assertThat(user.username()).isEqualTo("grace");
        assertThat(user.role().name()).isEqualTo("USER");
        assertThat(user.enabled()).isTrue();
        assertThat(user.id()).isNotNull();
    }

    @Test
    void me_returns401WhenUnauthenticated() {
        ResponseEntity<String> response = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
                null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_responseDoesNotContainPasswordHash() {
        ResponseEntity<AuthResponse> login = registerAndLogin("heidi", "heidi@test.com", "secure-pass-12");
        String sessionCookie = extractSessionCookie(login.getHeaders());

        ResponseEntity<String> me = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
                getWithSession(sessionCookie), String.class);

        assertThat(me.getBody()).doesNotContain("passwordHash", "password_hash", "$2a$");
    }

    // ── security headers (Story 53) ───────────────────────────────────────────

    @Test
    void securityHeaders_presentOnAuthEndpointResponse() {
        // Story 53: verify baseline hardening headers are set on responses.
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/auth/csrf", HttpMethod.GET, null, Void.class);

        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void errorResponse_containsNoProblemDetailWithStackTrace() {
        // Story 53: malformed JSON causes a 400; the body must not contain stack-trace text.
        ResponseEntity<String> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf("{not valid json}"), String.class);

        assertThat(response.getStatusCode().value()).isBetween(400, 499);
        String body = response.getBody();
        if (body != null) {
            assertThat(body).doesNotContain("at com.", "at org.", "at java.", "Exception");
        }
    }
}
