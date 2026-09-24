package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ticket-09 coverage at the HTTP seam: register → login → hello, plus the
 * CSRF/session mechanics the SPA depends on. Tests drive the real path —
 * they bootstrap a CSRF token from {@code GET /api/auth/csrf} and send it
 * back as the {@code X-XSRF-TOKEN} header exactly like the SPA's fetch
 * wrapper — through the shared {@link ApiTestSupport} fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    IpThrottleService ipThrottle;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
        // The IP-throttle cache is a context-scoped singleton — failed logins
        // in one test would otherwise throttle this suite's shared 127.0.0.1.
        ipThrottle.clear();
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    void registerCreatesEnabledUserAccount() throws Exception {
        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("USER"));

        User alice = userRepository.findByUsername("alice").orElseThrow();
        assertThat(alice.getId()).isPositive();
        assertThat(alice.getUsername()).isEqualTo("alice");
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(alice.getRole()).isEqualTo(Role.USER);
        assertThat(alice.isEnabled()).isTrue();
        assertThat(alice.getCreatedAt()).isNotNull();
        // BCrypt hash stored — never the plaintext
        assertThat(alice.getPasswordHash()).startsWith("$2");
        assertThat(alice.getPasswordHash()).isNotEqualTo(VALID_PASSWORD);
        assertThat(passwordEncoder.matches(VALID_PASSWORD, alice.getPasswordHash())).isTrue();
    }

    @Test
    void registerAcceptsPasswordAtExactlyMinimumLength() throws Exception {
        // Boundary of the length policy: 12 chars is the configured minimum,
        // so a 12-char password must be accepted (kills off-by-one mutants).
        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"exactly-12ch\"}")
            .andExpect(status().isCreated());

        assertThat(userRepository.findByUsername("alice")).isPresent();
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        register("alice", "alice@example.com", VALID_PASSWORD);

        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"other@example.com\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isConflict())
            .andExpect(header().string("Content-Type", Matchers.containsString("application/problem+json")))
            .andExpect(jsonPath("$.title").value("Username already taken"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        register("alice", "alice@example.com", VALID_PASSWORD);

        postWithCsrf("/api/auth/register",
                "{\"username\":\"bob\",\"email\":\"alice@example.com\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isConflict())
            .andExpect(header().string("Content-Type", Matchers.containsString("application/problem+json")))
            .andExpect(jsonPath("$.title").value("Email already registered"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"too-short\"}")
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Content-Type", Matchers.containsString("application/problem+json")))
            .andExpect(jsonPath("$.title").value("Password too short"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void registerRejectsOverLengthFields() throws Exception {
        // F-05 ceilings — username 64, email 254, password 128. Violations
        // surface as 400 problem+json via the ResponseEntityExceptionHandler
        // base, before the service runs (an unbounded password is a
        // BCrypt-hashing amplifier on an anonymous endpoint).
        postWithCsrf("/api/auth/register",
                "{\"username\":\"" + "u".repeat(65) + "\",\"email\":\"alice@example.com\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Content-Type",
                Matchers.containsString("application/problem+json")));
        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"" + "a".repeat(243) + "@example.com\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isBadRequest());
        postWithCsrf("/api/auth/register",
                "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"" + "p".repeat(129) + "\"}")
            .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Test
    void loginWithCorrectCredentialsCreatesSession() throws Exception {
        seedUser("alice", "alice@example.com");

        MvcResult result = postWithCsrf("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "login must set the Spring Session cookie");
        assertThat(session.isHttpOnly()).isTrue();

        // SessionAuthenticationStrategy must run on login: the
        // CsrfAuthenticationStrategy leg rotates the token, which the client
        // observes as a cleared XSRF-TOKEN cookie. Without the strategy call
        // (session-fixation protection!) no clearing cookie is emitted.
        Cookie rotatedCsrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(rotatedCsrf,
            "login must run the SessionAuthenticationStrategy (CSRF rotation)");
        assertThat(rotatedCsrf.getMaxAge()).isZero();
        assertThat(rotatedCsrf.getValue()).isEmpty();
    }

    @Test
    void loginFailureIsIdenticalForWrongPasswordAndUnknownUser() throws Exception {
        seedUser("alice", "alice@example.com");

        String wrongPasswordBody = problemDetail(postWithCsrf("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"wrong wrong wrong\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Content-Type", Matchers.containsString("application/problem+json")))
            .andExpect(jsonPath("$.title").value("Login failed"))
            .andReturn());

        String unknownUserBody = problemDetail(postWithCsrf("/api/auth/login",
                "{\"username\":\"nosuch\",\"password\":\"anything-anything\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Content-Type", Matchers.containsString("application/problem+json")))
            .andReturn());

        assertThat(wrongPasswordBody)
            .isEqualTo(unknownUserBody)
            .isEqualTo("Invalid username or password.");
    }

    @Test
    void loginRejectsOverLengthFields() throws Exception {
        // Same F-05 ceilings on the login DTO — 400, not the generic 401:
        // a malformed-shape request never reaches credential verification.
        postWithCsrf("/api/auth/login",
                "{\"username\":\"" + "u".repeat(65) + "\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isBadRequest());
        postWithCsrf("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"" + "p".repeat(129) + "\"}")
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // CSRF
    // ------------------------------------------------------------------

    @Test
    void csrfBootstrapEndpointEmitsTokenCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void csrfTokenCookieCarriesSameSiteStrict() throws Exception {
        // CookieCsrfTokenRepository doesn't emit SameSite on its own — the
        // cookie customizer adds it (F-06). SameSite is a Servlet 6.1
        // cookie attribute: a real container renders it into Set-Cookie;
        // the mock response's header string doesn't serialize attributes
        // on plain Cookies, so assert on the cookie object itself.
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();

        Cookie xsrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "CSRF bootstrap must emit the XSRF-TOKEN cookie");
        assertThat(xsrfCookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(xsrfCookie.isHttpOnly()).isFalse();
    }

    @Test
    void mutatingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"" + VALID_PASSWORD + "\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"a@b.co\",\"password\":\"" + VALID_PASSWORD + "\"}"))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Session persistence + protected endpoints
    // ------------------------------------------------------------------

    @Test
    void sessionIsPersistedInSpringSessionTable() throws Exception {
        seedUser("alice", "alice@example.com");
        loginSession("alice", VALID_PASSWORD);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void helloRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/hello"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void helloGreetsAuthenticatedUserByName() throws Exception {
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);

        mockMvc.perform(get("/api/hello").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, alice"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        // /api/auth/** is permitAll in the chain, so the endpoint itself must
        // reject anonymous callers with 401 (not a redirect or 403).
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsPrincipalWhenAuthenticated() throws Exception {
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginRejectsLockedAccount() throws Exception {
        // locked_until in the future must reject even correct credentials —
        // surfaced as the same generic 401 (no enumeration signal). Instants
        // truncated to seconds so the H2 roundtrip compares equal.
        java.time.Instant lockedUntil = java.time.Instant.now().plusSeconds(3600)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        java.time.Instant lastFailed = java.time.Instant.now().minusSeconds(60)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        User locked = seedUser("alice", "alice@example.com");
        locked.setLockedUntil(lockedUntil);
        locked.setFailedLoginAttempts(3);
        locked.setLastFailedAt(lastFailed);
        userRepository.save(locked);

        postWithCsrf("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        User persisted = userRepository.findByUsername("alice").orElseThrow();
        assertThat(persisted.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(persisted.getLastFailedAt()).isEqualTo(lastFailed);
        assertThat(persisted.getLockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void loginRejectsDisabledAccount() throws Exception {
        // enabled=false is the deliberate admin switch — same generic 401.
        User disabled = seedUser("alice", "alice@example.com");
        disabled.setEnabled(false);
        userRepository.save(disabled);

        postWithCsrf("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"" + VALID_PASSWORD + "\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void adminLoginReturnsAdminRole() throws Exception {
        // roleOf must skip non-ROLE_ authorities (Security 7 tokens also carry
        // FACTOR_PASSWORD) — an ADMIN user must report ADMIN, not the orElse
        // fallback and not a factor authority.
        User admin = seedUser("root", "root@example.com");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        Cookie session = loginSession("root", VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("root"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MvcResult register(String username, String email, String password) throws Exception {
        return postWithCsrf("/api/auth/register",
                "{\"username\":\"" + username + "\",\"email\":\"" + email
                    + "\",\"password\":\"" + password + "\"}")
            .andExpect(status().isCreated())
            .andReturn();
    }

    private String problemDetail(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.detail");
    }
}
