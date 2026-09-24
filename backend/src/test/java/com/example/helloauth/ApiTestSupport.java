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
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared HTTP-seam fixture for the {@code @SpringBootTest} + MockMvc suites
 * (extracted in the ticket-14 correction pass — the same helper block had
 * been copy-pasted into eight test classes since ticket 10 and the copies
 * had started to drift). Every method drives the API exactly like the SPA:
 * CSRF bootstrap via {@code GET /api/auth/csrf}, {@code X-XSRF-TOKEN} on
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
    // CSRF bootstrap
    // ------------------------------------------------------------------

    /** GET /api/auth/csrf and return the emitted XSRF-TOKEN cookie. */
    protected Cookie csrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie token = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(token, "CSRF bootstrap must emit the XSRF-TOKEN cookie");
        return token;
    }

    // ------------------------------------------------------------------
    // Login — raw attempts (assert the outcome yourself) and the session
    // ------------------------------------------------------------------

    /** POST login (with CSRF) without asserting the outcome. */
    protected ResultActions login(String username, String password)
            throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/login")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\""
                + password + "\"}"));
    }

    /** POST login (with CSRF) from a specific source IP. */
    protected ResultActions login(String username, String password,
            String remoteAddr) throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/login")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .remoteAddress(remoteAddr)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\""
                + password + "\"}"));
    }

    /**
     * POST login with a (spoofable) {@code X-Forwarded-For} header — proves
     * the IP layer keys on {@code getRemoteAddr()}, not the untrusted header.
     */
    protected ResultActions loginWithXff(String username, String password,
            String remoteAddr, String xff) throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/login")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .header("X-Forwarded-For", xff)
            .remoteAddress(remoteAddr)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\""
                + password + "\"}"));
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
        Cookie csrf = csrfToken();
        return mockMvc.perform(post(url)
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
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
        Cookie csrf = csrfToken();
        return mockMvc.perform(post(url)
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .remoteAddress(remoteAddr)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /** PATCH {@code json} to {@code url} as the holder of {@code session}. */
    protected ResultActions patchWithCsrf(
            String url, Cookie session, String json) throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(patch(url)
            .cookie(session, csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /** DELETE {@code url} as the holder of {@code session}. */
    protected ResultActions deleteWithCsrf(String url, Cookie session)
            throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(delete(url)
            .cookie(session, csrf)
            .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    // ------------------------------------------------------------------
    // Password reset endpoints
    // ------------------------------------------------------------------

    /** POST a reset request for {@code email} (with CSRF). */
    protected ResultActions requestReset(String email) throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/password-reset/request")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}"));
    }

    /** POST a reset confirm for {@code token} (with CSRF). */
    protected ResultActions confirmReset(String token, String newPassword)
            throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"newPassword\":\""
                + newPassword + "\"}"));
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
