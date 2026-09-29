package com.example.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * HTTP-boundary integration tests for admin user management + authorization
 * (Stories 25–34, 43–44, 49). Real Spring Security filter chain, real H2, real
 * Spring Session JDBC, real controller/service/repository stack.
 *
 * Admin accounts are seeded directly via the repository since admin bootstrap
 * is Slice 7. Passwords are BCrypt-encoded with the real PasswordEncoder so
 * HTTP login works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:admintest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class AdminUserIntegrationTest {

    private static final String PASSWORD = "secure-pass-12";

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    // ── seeding + auth helpers ─────────────────────────────────────────────────

    private User seed(String username, String email, Role role) {
        return users.save(new User(username, email, passwordEncoder.encode(PASSWORD), role, Instant.now()));
    }

    private String csrfToken() {
        HttpHeaders headers = restTemplate.exchange("/api/auth/csrf", HttpMethod.GET, null, Void.class).getHeaders();
        return valueOfCookie(headers, "XSRF-TOKEN");
    }

    private String valueOfCookie(HttpHeaders headers, String name) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith(name + "=")) {
                    return c.substring((name + "=").length()).split(";")[0];
                }
            }
        }
        return null;
    }

    /** Logs in and returns the SESSION cookie (e.g. "SESSION=abc"). */
    private String login(String username) {
        String csrf = csrfToken();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrf);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        ResponseEntity<Void> resp = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, PASSWORD), h),
                Void.class);
        assertThat(resp.getStatusCode()).as("login should succeed for " + username).isEqualTo(HttpStatus.OK);
        String sessionValue = valueOfCookie(resp.getHeaders(), "SESSION");
        assertThat(sessionValue).as("login should establish a session for " + username).isNotNull();
        // Return the full cookie pair ("SESSION=<value>") for use in the Cookie header.
        return "SESSION=" + sessionValue;
    }

    /** Headers for a state-changing admin request: session + CSRF double-submit. */
    private HttpHeaders authed(String sessionCookie) {
        String csrf = csrfToken();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrf);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf + "; " + sessionCookie);
        return h;
    }

    private HttpHeaders sessionOnly(String sessionCookie) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.COOKIE, sessionCookie);
        return h;
    }

    // ── authorization gate (Stories 26–27, 49) ─────────────────────────────────

    @Test
    void anonymous_cannotAccessAdmin_returns401() {
        assertThat(restTemplate.exchange("/api/admin/users", HttpMethod.GET, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void user_cannotAccessAdmin_returns403() {
        seed("user", "user@admin.test", Role.USER);
        String session = login("user");

        assertThat(restTemplate.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(session)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void user_craftedAdminMutation_returns403NotBypassed() {
        // Story 49 / IDOR: a USER cannot mutate any account via admin endpoints,
        // regardless of the target id supplied.
        seed("plainuser", "plain@admin.test", Role.USER);
        User victim = seed("victim", "victim@admin.test", Role.USER);
        String session = login("plainuser");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + victim.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Victim remains enabled — the request never reached the service
        assertThat(users.findById(victim.getId()).orElseThrow().isEnabled()).isTrue();
    }

    // ── admin happy paths (Stories 25, 28, 31, 33) ─────────────────────────────

    @Test
    void admin_canListUsers_withoutPasswordHash() {
        seed("admin", "admin@admin.test", Role.ADMIN);
        seed("alice", "alice@admin.test", Role.USER);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("alice", "admin");
        assertThat(resp.getBody()).doesNotContain("passwordHash", "password_hash", "$2a$");
    }

    @Test
    void admin_canDisableAndEnableUser() {
        seed("admin", "admin@admin.test", Role.ADMIN);
        User target = seed("target", "target@admin.test", Role.USER);
        String session = login("admin");

        ResponseEntity<String> disable = restTemplate.exchange(
                "/api/admin/users/" + target.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(session)), String.class);
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(target.getId()).orElseThrow().isEnabled()).isFalse();

        ResponseEntity<String> enable = restTemplate.exchange(
                "/api/admin/users/" + target.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":true}", authed(session)), String.class);
        assertThat(enable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(target.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void admin_canChangeUserRole() {
        seed("admin", "admin@admin.test", Role.ADMIN);
        User target = seed("target", "target@admin.test", Role.USER);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + target.getId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>("{\"role\":\"ADMIN\"}", authed(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void admin_canDeleteUser() {
        seed("admin", "admin@admin.test", Role.ADMIN);
        User target = seed("target", "target@admin.test", Role.USER);
        String session = login("admin");

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/admin/users/" + target.getId(), HttpMethod.DELETE,
                new HttpEntity<>(authed(session)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(users.findById(target.getId())).isEmpty();
    }

    @Test
    void unknownTargetId_returns404() {
        seed("admin", "admin@admin.test", Role.ADMIN);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + UUID.randomUUID() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(session)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── self-action guard (Stories 30, 32, 34) ─────────────────────────────────

    @Test
    void admin_cannotDisableSelf_returns409() {
        User admin = seed("admin", "admin@admin.test", Role.ADMIN);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + admin.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Still enabled — the guard fired before any mutation
        assertThat(users.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void admin_cannotChangeOwnRole_returns409() {
        User admin = seed("admin", "admin@admin.test", Role.ADMIN);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + admin.getId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>("{\"role\":\"USER\"}", authed(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void admin_cannotDeleteSelf_evenWithCraftedOwnId_returns409() {
        // Story 49: self is resolved from the authenticated principal; supplying
        // one's own id as the target still triggers the 409 guard.
        User admin = seed("admin", "admin@admin.test", Role.ADMIN);
        String session = login("admin");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/admin/users/" + admin.getId(), HttpMethod.DELETE,
                new HttpEntity<>(authed(session)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(users.findById(admin.getId())).isPresent();
    }

    // ── stale-session invalidation (Stories 43–44) ─────────────────────────────

    @Test
    void disablingUser_invalidatesExistingSession() {
        // Story 43: a disabled user's live session can no longer reach protected endpoints.
        seed("admin", "admin@admin.test", Role.ADMIN);
        User target = seed("target", "target@admin.test", Role.USER);

        String adminSession = login("admin");
        String targetSession = login("target");

        // Target's session works before disable
        assertThat(restTemplate.exchange("/api/hello", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(targetSession)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Admin disables the target
        restTemplate.exchange("/api/admin/users/" + target.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(adminSession)), String.class);

        // Target's pre-existing session is now rejected
        assertThat(restTemplate.exchange("/api/hello", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(targetSession)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void downgradingAdmin_invalidatesSessionSoStaleAdminAuthorityIsLost() {
        // Story 44: after downgrade, the target's stale session cannot retain ADMIN access.
        seed("superadmin", "super@admin.test", Role.ADMIN);
        User target = seed("targetadmin", "targetadmin@admin.test", Role.ADMIN);

        String superSession = login("superadmin");
        String targetSession = login("targetadmin");

        // Target (an admin) can reach admin endpoints before downgrade
        assertThat(restTemplate.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(targetSession)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Superadmin downgrades target to USER
        restTemplate.exchange("/api/admin/users/" + target.getId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>("{\"role\":\"USER\"}", authed(superSession)), String.class);

        // Target's stale session is invalidated — no lingering ADMIN authority (401, not 200/403)
        assertThat(restTemplate.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(sessionOnly(targetSession)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── audit (Story 40 / 48) ──────────────────────────────────────────────────

    @Test
    void adminAction_emitsAuditEventWithoutSecrets(CapturedOutput output) {
        seed("admin", "admin@admin.test", Role.ADMIN);
        User target = seed("target", "target@admin.test", Role.USER);
        String session = login("admin");

        restTemplate.exchange("/api/admin/users/" + target.getId() + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\":false}", authed(session)), String.class);

        assertThat(output.getOut()).contains("event=admin_user_action", "action=disable", "target=target");
        // No password hash ever appears in logs
        assertThat(output.getOut()).doesNotContain("$2a$", "passwordHash");
    }
}
