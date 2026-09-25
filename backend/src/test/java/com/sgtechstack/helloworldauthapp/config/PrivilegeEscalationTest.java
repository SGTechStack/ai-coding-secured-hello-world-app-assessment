package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.sgtechstack.helloworldauthapp.auth.RequestRateLimiter;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Privilege-escalation suite: asks specifically whether a {@code USER} can
 * reach or influence anything reserved for {@code ADMIN}.
 *
 * <p>{@link SecurityConfigTest} pins the guard matrix as designed (the happy
 * path of each rule). This class attacks it from the outside: every admin route
 * from a USER session, the role field as a mass-assignment vector, path
 * variations that might dodge a matcher, and whether a revoked admin's session
 * really stops working. The two overlap on {@code GET /api/admin/users} on
 * purpose — that route is the canary for the whole matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivilegeEscalationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String ADMIN_A = "escalationadmina";
    private static final String ADMIN_B = "escalationadminb";
    private static final String REGULAR = "escalationuser";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RequestRateLimiter requestRateLimiter;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // Two admins so that a demotion in the session-revocation test is not
        // blocked by LastAdminGuard.
        userRepository.save(new User(ADMIN_A, "escalationadmina@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        userRepository.save(new User(ADMIN_B, "escalationadminb@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        userRepository.save(new User(REGULAR, "escalationuser@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true));
        // POST /api/auth/register is rate limited to 10/hour per caller and the
        // limiter is a singleton shared by every test in this context. Without
        // this, the mass-assignment test below fails with 429 depending on which
        // classes ran first.
        requestRateLimiter.reset();
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private UUID idOf(String username) {
        return userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
    }

    private Role roleOf(String username) {
        return userRepository.findByUsernameIgnoreCase(username).orElseThrow().getRole();
    }

    /**
     * Performs the request and returns the status, treating a firewall
     * rejection (which surfaces as a thrown exception under MockMvc rather
     * than a response) as blocked. Used by the path-variation test, where the
     * question is only "did anything succeed", not "which layer said no".
     */
    private int statusOf(RequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception rejectedByFirewall) {
            return -1;
        }
    }

    /**
     * Records the outcome of one bypass attempt. Collected rather than
     * asserted one at a time so a run reports the whole probe table instead of
     * stopping at the first variant that slips through — when this test fails,
     * the useful information is which variants behave differently from the
     * rest, not merely that one did.
     */
    private record Probe(String label, int status) {
        boolean succeeded() {
            return status >= 200 && status < 300;
        }

        String describe() {
            return "%-38s -> %s".formatted(label, status == -1 ? "rejected by HttpFirewall" : status);
        }
    }

    private Probe probe(String label, RequestBuilder request) {
        return new Probe(label, statusOf(request));
    }

    // ---------------------------------------------------------------
    // 1. The authority set itself
    // ---------------------------------------------------------------

    @Test
    void userSessionHoldsNoAdminAuthorityAtAll() throws Exception {
        // The root question, asked below the HTTP layer: regardless of which
        // routes exist, a USER principal must never carry an ADMIN_* authority
        // or ROLE_ADMIN. If this holds, no URL guard keyed on an admin
        // authority can match a USER, including guards added in future.
        MockHttpSession userSession = login(REGULAR);

        SecurityContext context = (SecurityContext) userSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

        assertThat(context.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "HELLO_READ", "ACCOUNT_SELF_MANAGE")
                .noneMatch(authority -> authority.startsWith("ADMIN_"))
                .doesNotContain("ROLE_ADMIN");
    }

    // ---------------------------------------------------------------
    // 2. Every admin route, from a USER session
    // ---------------------------------------------------------------

    @Test
    void everyAdminRouteIsForbiddenForARegularUser() throws Exception {
        MockHttpSession userSession = login(REGULAR);
        UUID targetAdmin = idOf(ADMIN_A);

        mockMvc.perform(get("/api/admin/users").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users/{id}/email", targetAdmin)
                        .param("purpose", "escalation attempt")
                        .session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetAdmin)
                        .with(csrf())
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/{id}/role", targetAdmin)
                        .with(csrf())
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/{id}", targetAdmin)
                        .with(csrf())
                        .session(userSession)
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isForbidden());

        // Nothing the rejected calls attempted may have landed.
        assertThat(roleOf(ADMIN_A)).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByUsernameIgnoreCase(ADMIN_A)).isPresent();
        assertThat(userRepository.findByUsernameIgnoreCase(ADMIN_A).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void everyAdminRouteIsUnauthorizedForAnonymousCallers() throws Exception {
        UUID targetAdmin = idOf(ADMIN_A);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users/{id}/email", targetAdmin).param("purpose", "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetAdmin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetAdmin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/users/{id}", targetAdmin).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // 3. Self-promotion
    // ---------------------------------------------------------------

    @Test
    void userCannotPromoteItselfThroughTheRoleEndpoint() throws Exception {
        MockHttpSession userSession = login(REGULAR);
        UUID self = idOf(REGULAR);

        mockMvc.perform(patch("/api/admin/users/{id}/role", self)
                        .with(csrf())
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertThat(roleOf(REGULAR)).isEqualTo(Role.USER);
    }

    @Test
    void userCannotEscalateByReplayingTheRoleEndpointWithoutCsrf() throws Exception {
        // Ordering check: the authority decision must not be reachable only
        // because CSRF happened to reject first, and vice versa. Either way the
        // role must not change.
        MockHttpSession userSession = login(REGULAR);
        UUID self = idOf(REGULAR);

        int status = statusOf(patch("/api/admin/users/{id}/role", self)
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"));

        assertThat(status).isIn(401, 403);
        assertThat(roleOf(REGULAR)).isEqualTo(Role.USER);
    }

    // ---------------------------------------------------------------
    // 4. Mass assignment at registration
    // ---------------------------------------------------------------

    @Test
    void registrationIgnoresAClientSuppliedRole() throws Exception {
        // RegistrationRequest is a record with no role component, so there is
        // nowhere for this field to bind. Asserted rather than assumed, because
        // adding a role component later would silently turn registration into
        // self-service admin.
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "massassign",
                                  "email": "massassign@example.com",
                                  "password": "correct-horse-battery",
                                  "role": "ADMIN",
                                  "authorities": ["ADMIN_USER_WRITE"],
                                  "enabled": true,
                                  "admin": true
                                }
                                """))
                .andExpect(status().isCreated());

        assertThat(roleOf("massassign")).isEqualTo(Role.USER);

        // And the authorities that principal actually receives on login.
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "massassign")
                        .param("password", "correct-horse-battery"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ADMIN_"));

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // 5. Path variations that might dodge a matcher
    // ---------------------------------------------------------------

    @Test
    void adminGuardsAreNotBypassableByPathVariation() throws Exception {
        MockHttpSession userSession = login(REGULAR);
        UUID targetAdmin = idOf(ADMIN_A);

        List<Probe> probes = List.of(
                probe("uppercase segment", get("/api/ADMIN/users").session(userSession)),
                probe("mixed case segments", get("/api/Admin/Users").session(userSession)),
                probe("trailing slash", get("/api/admin/users/").session(userSession)),
                probe("double slash", get("/api//admin/users").session(userSession)),
                probe("dot segment", get("/api/admin/./users").session(userSession)),
                probe("traversal within admin", get("/api/admin/users/../users").session(userSession)),
                probe("traversal from whitelisted route",
                        get("/api/health/../admin/users").session(userSession)),
                probe("encoded slash", get("/api/admin%2fusers").session(userSession)),
                probe("path parameter (semicolon)", get("/api/admin/users;foo=bar").session(userSession)),
                probe("extension suffix", get("/api/admin/users.json").session(userSession)),
                probe("method override header", post("/api/admin/users")
                        .with(csrf())
                        .header("X-HTTP-Method-Override", "GET")
                        .session(userSession)),
                probe("wrong method on guarded path", post("/api/admin/users")
                        .with(csrf())
                        .session(userSession)),
                probe("extra segment under email guard",
                        get("/api/admin/users/{id}/email/extra", targetAdmin).session(userSession)),
                probe("case-varied email guard",
                        get("/api/admin/users/{id}/EMAIL", targetAdmin).session(userSession)));

        System.out.println("Admin-guard bypass probe (USER session):\n  "
                + probes.stream().map(Probe::describe).collect(Collectors.joining("\n  ")));

        assertThat(probes)
                .withFailMessage("path variation reached an admin surface: %s",
                        probes.stream().filter(Probe::succeeded).map(Probe::describe).toList())
                .noneMatch(Probe::succeeded);

        assertThat(roleOf(REGULAR)).isEqualTo(Role.USER);
    }

    // ---------------------------------------------------------------
    // 6. Stale authority in a live session
    // ---------------------------------------------------------------

    @Test
    void demotedAdminLosesAdminAccessImmediately() throws Exception {
        MockHttpSession victimSession = login(ADMIN_A);
        MockHttpSession actorSession = login(ADMIN_B);

        // Confirm the session is genuinely admin-capable first, so a later
        // failure cannot be mistaken for it never having worked.
        mockMvc.perform(get("/api/admin/users").session(victimSession))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{id}/role", idOf(ADMIN_A))
                        .with(csrf())
                        .session(actorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk());

        assertThat(roleOf(ADMIN_A)).isEqualTo(Role.USER);

        // The demoted principal's existing session must not keep its old
        // authorities. Authorities are cached in the session's SecurityContext,
        // so this only holds because the role change revokes sessions.
        int status = statusOf(get("/api/admin/users").session(victimSession));
        assertThat(status)
                .withFailMessage("demoted admin still reached /api/admin/users with status %d", status)
                .isIn(401, 403);
    }

    @Test
    void promotedUserDoesNotGainAdminAccessOnAnAlreadyIssuedSession() throws Exception {
        // The inverse staleness case. A USER session that existed before the
        // promotion must not silently become an admin session; the principal has
        // to re-authenticate. This matters because it is the same cache that the
        // demotion case depends on.
        MockHttpSession userSession = login(REGULAR);
        MockHttpSession actorSession = login(ADMIN_B);

        mockMvc.perform(patch("/api/admin/users/{id}/role", idOf(REGULAR))
                        .with(csrf())
                        .session(actorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        int status = statusOf(get("/api/admin/users").session(userSession));
        assertThat(status)
                .withFailMessage("pre-promotion session was silently upgraded (status %d)", status)
                .isIn(401, 403);
    }

    // ---------------------------------------------------------------
    // 7. Disabled account
    // ---------------------------------------------------------------

    @Test
    void disabledUserCannotAuthenticateAtAll() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase(REGULAR).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", REGULAR)
                        .param("password", PASSWORD))
                .andExpect(status().is4xxClientError());
    }
}
