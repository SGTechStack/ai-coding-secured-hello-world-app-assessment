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

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the config-driven URL guard matrix end-to-end via real HTTP
 * requests, rather than unit-testing {@code SecurityConfig} in isolation.
 * {@link SecurityPropertiesTest} covers the raw YAML-to-{@link
 * SecurityProperties} binding; {@link RoleHierarchyConfigTest} covers the
 * {@code RoleHierarchy} bean in isolation. This class covers the two
 * together, as the filter chain actually applies them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigTest {

    private static final String ADMIN_USERNAME = "guardmatrixadmin";
    private static final String ADMIN_PASSWORD = "correct-horse-battery";
    private static final String REGULAR_USERNAME = "guardmatrixuser";
    private static final String REGULAR_PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Tokens FK-reference users; clear first so a leftover row from
        // another test class doesn't block this class's user deletes.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(
                ADMIN_USERNAME, "guardmatrixadmin@example.com", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN,
                true));
        userRepository.save(new User(
                REGULAR_USERNAME, "guardmatrixuser@example.com", passwordEncoder.encode(REGULAR_PASSWORD), Role.USER,
                true));
    }

    private MockHttpSession loginAndGetSession(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @Test
    void adminReachesUserGuardedRouteThroughRoleHierarchyAlone() throws Exception {
        // HELLO_READ is only granted to USER in role-mappings; ADMIN reaches
        // it purely because RoleHierarchy expands ROLE_ADMIN to include
        // ROLE_USER's authorities at authentication time.
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/hello").session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserReachesItsOwnDirectlyGrantedRoute() throws Exception {
        MockHttpSession userSession = loginAndGetSession(REGULAR_USERNAME, REGULAR_PASSWORD);

        mockMvc.perform(get("/api/hello").session(userSession))
                .andExpect(status().isOk());
    }

    @Test
    void adminAuthenticationHasRoleUserAuthorityExpandedByHierarchy() throws Exception {
        // Proves the expansion happens at authentication time (via the
        // RoleHierarchyAuthoritiesMapper-equipped DaoAuthenticationProvider),
        // independent of which HTTP routes happen to be guarded today.
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);

        SecurityContext securityContext = (SecurityContext) adminSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

        assertThat(securityContext.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void unmappedPathIsDeniedByDefaultEvenWhenAuthenticated() throws Exception {
        // Proves the zero-trust default: a path that is neither whitelisted
        // nor covered by any url-guard entry must be unreachable, including
        // for an authenticated admin, rather than silently falling back to
        // "any logged-in user may call it" (the old anyRequest().authenticated()
        // behaviour this guard matrix replaces).
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/this-route-does-not-exist-anywhere").session(adminSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void unmappedPathIsDeniedForAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/this-route-does-not-exist-anywhere"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyWhitelistedRouteIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/csrf")).andExpect(status().isOk());
        // register/login/logout/password-reset are exercised end-to-end by
        // AuthControllerTest/PasswordResetTest/AdminUserControllerTest; this
        // class only re-confirms the two purely-GET whitelist entries that
        // aren't already covered elsewhere, to avoid duplicating state setup.
    }

    @Test
    void helloIsUnreachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminUsersRouteIsReachableOnlyByAdmin() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        MockHttpSession userSession = loginAndGetSession(REGULAR_USERNAME, REGULAR_PASSWORD);

        mockMvc.perform(get("/api/admin/users").session(adminSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").session(userSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserWriteRoutesAreReachableOnlyByAdmin() throws Exception {
        MockHttpSession userSession = loginAndGetSession(REGULAR_USERNAME, REGULAR_PASSWORD);
        User target = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", target.getId())
                        .with(csrf())
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/{id}", target.getId())
                        .with(csrf())
                        .session(userSession))
                .andExpect(status().isForbidden());
    }
}
