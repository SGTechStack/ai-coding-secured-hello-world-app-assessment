package com.sgtechstack.helloworldauthapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.admin.SetEnabledRequest;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF protection was configured correctly and never asserted.
 *
 * <p>That is a specific kind of gap. The control worked, so no test failed and
 * nothing looked wrong — but every test in the suite obtained a token through
 * {@code .with(csrf())}, so not one of them would have noticed if the protection
 * stopped applying. A single line in the security DSL stands between a
 * cookie-authenticated API and any page on the internet being able to act as a
 * signed-in user, and a refactor that removed it would have gone in green.
 *
 * <p>These tests approach from the other side: they omit the token and require a
 * refusal. The assertion is therefore about the control being present, not about
 * the happy path working.
 *
 * <p>Note what is <em>not</em> claimed here. These prove the server rejects a
 * request without a token. They do not prove a cross-origin page cannot obtain
 * one — that rests on the {@code SameSite=Lax} cookie attribute and the CORS
 * allow-list, covered by {@link SessionCookieAttributesTest} and
 * {@link CorsOriginRejectionTest}. All three are needed; each alone is
 * insufficient.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CsrfProtectionTest {

    private static final String ADMIN_USERNAME = "csrf-test-admin";
    private static final String ADMIN_PASSWORD = "csrf-admin-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(ADMIN_USERNAME, "csrf-admin@example.com",
                passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN, true));
    }

    @Test
    void anAuthenticatedMutationWithoutTheTokenHeaderIsRejected() throws Exception {
        MockHttpSession adminSession = login();

        // A valid session and no CSRF token: exactly the shape of a request a
        // hostile page can cause a logged-in admin's browser to send, since the
        // cookie rides along automatically and the token does not.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", UUID.randomUUID())
                        .session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeleteWithoutTheTokenHeaderIsRejectedBeforeAnythingElseIsChecked() throws Exception {
        MockHttpSession adminSession = login();

        // The CSRF filter runs ahead of the handler, so this is refused without
        // the id being looked up or the confirmation password being considered.
        // Worth pinning: the delete path now has several guards, and CSRF has to
        // be the first of them rather than one that a later check happens to
        // cover.
        mockMvc.perform(delete("/api/admin/users/{id}", UUID.randomUUID())
                        .session(adminSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedWriteWithoutTheTokenHeaderIsRejected() throws Exception {
        // Registration is whitelisted for authentication but not exempt from
        // CSRF. Both halves matter: "no session required" and "no token
        // required" are different statements, and the whitelist only makes the
        // first one.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"csrfless","email":"csrfless@example.com",\
                                "password":"password-1234-long"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutWithoutTheTokenHeaderIsRejected() throws Exception {
        MockHttpSession adminSession = login();

        // Forced logout is the mildest CSRF outcome and still worth refusing:
        // it is a denial of service against a signed-in user, and an exemption
        // here would be an exemption on a state-changing endpoint.
        mockMvc.perform(post("/api/auth/logout").session(adminSession))
                .andExpect(status().isForbidden());

        // Still signed in, which is what makes the rejection meaningful rather
        // than merely a different status code on the way out.
        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void theTokenEndpointHandsOutAUsableToken() throws Exception {
        // Whether the token also arrives as a JavaScript-readable cookie cannot be
        // asserted here, and that is a limitation of the test tooling rather than a
        // gap in the control. SecurityMockMvcRequestPostProcessors.csrf() — used by
        // every mutation test in this suite — substitutes the CsrfFilter's token
        // repository for a test double that keeps the token in a request attribute,
        // so once any test in the shared context has used it, no CookieCsrfTokenRepository
        // cookie is written for the rest of the run. In isolation this class saw the
        // cookie; in the full suite it did not, which is precisely the kind of
        // order-dependent assertion that gets dismissed as a flake.
        //
        // The cookie's attributes are therefore asserted in SessionCookieAttributesTest,
        // over a real servlet container where nothing is substituted.
        // The substitution reaches the token's own header name too, so even that is
        // not safely assertable from here. The header the SPA actually has to send
        // is exercised for real by SessionCookieAttributesTest, which logs in over a
        // live container using X-XSRF-TOKEN.
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void theSameMutationSucceedsOnceTheTokenIsPresent() throws Exception {
        MockHttpSession adminSession = login();
        User target = userRepository.save(new User("csrf-target", "csrf-target@example.com",
                passwordEncoder.encode("target-password-1234"), Role.USER, true));

        // The control test. Without this, every assertion above could be passing
        // because of something unrelated to CSRF — a wrong path, a missing
        // authority — and would keep passing if CSRF were disabled.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", target.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk());
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", ADMIN_USERNAME)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
