package com.example.helloauth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helloauth.passwordreset.EmailService;
import com.example.helloauth.passwordreset.PasswordResetService;
import com.example.helloauth.passwordreset.PasswordResetToken;
import com.example.helloauth.passwordreset.PasswordResetTokenRepository;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared HTTP-seam fixture for the {@code @SpringBootTest} + MockMvc suites
 * (extracted in the ticket-14 correction pass — the same helper block had
 * been copy-pasted into eight test classes since ticket 10 and the copies
 * had started to drift). Every method drives the API exactly like the SPA:
 * CSRF bootstrap via {@code GET /api/auth/csrf} (token from the JSON body,
 * held by the returned session), {@code X-XSRF-TOKEN} on
 * every mutation, session cookie from a real login. Fixtures are seeded
 * through the repositories per the ratified strategy (ticket 05).
 *
 * <p>Subclasses keep their own {@code @SpringBootTest} annotations (the
 * property/property-source customization differs per suite) and their own
 * collaborators (clocks, throttles, mocks); this class owns only the
 * HTTP-driving plumbing and the repository-seeded fixtures.
 */
abstract class ApiTestSupport {

    /** The password every seeded user gets — comfortably above the 12-char floor. */
    protected static final String VALID_PASSWORD = "correct horse battery";

    /** Reset-confirm's replacement password in the reset suites. */
    protected static final String NEW_PASSWORD = "brand new passphrase";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordResetTokenRepository tokenRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // Repository-seeded fixtures (ticket 05 strategy)
    // ------------------------------------------------------------------

    /** A persisted, enabled USER whose password is {@link #VALID_PASSWORD}. */
    protected User seedUser(String username, String email) {
        return seedUser(username, email, Role.USER);
    }

    /** A persisted, enabled user with the given role. */
    protected User seedUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(VALID_PASSWORD));
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    /** A live reset-token row for {@code user} — only the hash is stored. */
    protected PasswordResetToken seedToken(
            User user, String plaintext, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(PasswordResetService.hashToken(plaintext));
        token.setExpiresAt(expiresAt);
        return tokenRepository.save(token);
    }

    // ------------------------------------------------------------------
    // CSRF bootstrap (Synchronizer Token: the token lives in the session)
    // ------------------------------------------------------------------

    /** The header the SPA echoes the token in. */
    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /**
     * A CSRF token plus the SESSION cookie of the server session that holds
     * it. The two only work together: the token is compared against the
     * copy stored in that session, so neither is valid on its own.
     */
    protected record CsrfSession(String token, Cookie session) {}

    /**
     * GET /api/auth/csrf anonymously, exactly like the SPA on page load:
     * the token comes from the JSON body (never a cookie) and a new
     * server-side session is created to hold it.
     */
    protected CsrfSession csrfToken() throws Exception {
        return csrfToken(null);
    }

    /**
     * GET /api/auth/csrf inside an existing session (e.g. after login). The
     * token is generated into that session; the cookie stays the same unless
     * the server issued a new one.
     */
    protected CsrfSession csrfToken(Cookie session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) {
            request.cookie(session);
        }
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        String token = JsonPath.read(
            result.getResponse().getContentAsString(), "$.token");
        assertNotNull(token, "CSRF bootstrap must return the token in the body");
        Cookie issued = result.getResponse().getCookie("SESSION");
        Cookie owner = issued != null ? issued : session;
        assertNotNull(owner, "the CSRF token must be held by a server session");
        return new CsrfSession(token, owner);
    }

    /** Adds a fresh anonymous-session CSRF token (cookie + header) to {@code request}. */
    protected MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request) throws Exception {
        CsrfSession csrf = csrfToken();
        return request.cookie(csrf.session()).header(CSRF_HEADER, csrf.token());
    }

    /** Adds a CSRF token generated inside {@code session} to {@code request}. */
    protected MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, Cookie session) throws Exception {
        CsrfSession csrf = csrfToken(session);
        return request.cookie(csrf.session()).header(CSRF_HEADER, csrf.token());
    }

    // ------------------------------------------------------------------
    // Login — raw attempts (assert the outcome yourself) and the session
    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder loginRequest(String username, String password) {
        return post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\""
                + password + "\"}");
    }

    /** POST login (with CSRF) without asserting the outcome. */
    protected ResultActions login(String username, String password)
            throws Exception {
        return mockMvc.perform(withCsrf(loginRequest(username, password)));
    }

    /** POST login (with CSRF) from a specific source IP. */
    protected ResultActions login(String username, String password,
            String remoteAddr) throws Exception {
        return mockMvc.perform(withCsrf(loginRequest(username, password))
            .remoteAddress(remoteAddr));
    }

    /**
     * POST login with a (spoofable) {@code X-Forwarded-For} header — proves
     * the IP layer keys on {@code getRemoteAddr()}, not the untrusted header.
     */
    protected ResultActions loginWithXff(String username, String password,
            String remoteAddr, String xff) throws Exception {
        return mockMvc.perform(withCsrf(loginRequest(username, password))
            .header("X-Forwarded-For", xff)
            .remoteAddress(remoteAddr));
    }

    /** POST login (with CSRF) and return the issued SESSION cookie. */
    protected Cookie loginSession(String username, String password)
            throws Exception {
        Cookie session = login(username, password)
            .andExpect(status().isOk())
            .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(session, "login must set the Spring Session cookie");
        return session;
    }

    // ------------------------------------------------------------------
    // Generic mutations with a fresh CSRF token
    // ------------------------------------------------------------------

    /** POST {@code json} to {@code url} with a fresh CSRF token. */
    protected ResultActions postWithCsrf(String url, String json)
            throws Exception {
        return mockMvc.perform(withCsrf(post(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /**
     * POST {@code json} to {@code url} from a specific source IP — the
     * anonymous-throttle tests key their buckets on
     * {@code getRemoteAddr()}, same as the login throttle.
     */
    protected ResultActions postWithCsrf(String url, String json,
            String remoteAddr) throws Exception {
        return mockMvc.perform(withCsrf(post(url))
            .remoteAddress(remoteAddr)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /** PATCH {@code json} to {@code url} as the holder of {@code session}. */
    protected ResultActions patchWithCsrf(
            String url, Cookie session, String json) throws Exception {
        return mockMvc.perform(withCsrf(patch(url), session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /** DELETE {@code url} as the holder of {@code session}. */
    protected ResultActions deleteWithCsrf(String url, Cookie session)
            throws Exception {
        return mockMvc.perform(withCsrf(delete(url), session));
    }

    // ------------------------------------------------------------------
    // Password reset endpoints
    // ------------------------------------------------------------------

    /** POST a reset request for {@code email} (with CSRF). */
    protected ResultActions requestReset(String email) throws Exception {
        return postWithCsrf("/api/auth/password-reset/request",
            "{\"email\":\"" + email + "\"}");
    }

    /** POST a reset confirm for {@code token} (with CSRF). */
    protected ResultActions confirmReset(String token, String newPassword)
            throws Exception {
        return postWithCsrf("/api/auth/password-reset/confirm",
            "{\"token\":\"" + token + "\",\"newPassword\":\""
                + newPassword + "\"}");
    }

    /**
     * The token the stubbed email carried — captured at the port seam. Takes
     * the suite's {@code @MockitoBean} so only the classes that need the
     * mock declare it.
     */
    protected String emailedToken(EmailService emailService) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLink(anyString(), captor.capture());
        return captor.getValue();
    }
}
