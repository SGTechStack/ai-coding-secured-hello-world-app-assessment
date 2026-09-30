package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Ticket 02: Spring Session JDBC + session-stored CSRF, driven through the real filter chain the
 * way the SPA does (see {@link ApiSession}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionAndCsrfFlowTest {

    private static final String USERNAME = "sessionflow";
    private static final String PASSWORD = "SessionFlowPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiters rateLimiters;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsername(USERNAME)
                .orElseGet(() -> new User(USERNAME, "sessionflow@example.com", passwordEncoder.encode(PASSWORD), "Flow"));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        rateLimiters.resetAll();
    }

    private ApiSession client() {
        return new ApiSession(mockMvc, objectMapper);
    }

    @Test
    void csrfEndpointReturnsHeaderNameAndTokenAndIsNotCacheable() throws Exception {
        MvcResult result = client().get("/api/auth/csrf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        // No JS-readable CSRF cookie any more: the only cookie is the HttpOnly session cookie.
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNull();
        String setCookie = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
        assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejectedWithCsrfCode() throws Exception {
        ApiSession client = client();
        client.get("/api/auth/csrf");
        client.postWithoutCsrf("/api/auth/login", Map.of("username", USERNAME, "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void loginRotatesSessionIdAndCsrfTokenAndAuthenticatesTheSession() throws Exception {
        ApiSession client = client();
        String tokenBefore = client.fetchCsrfToken();
        String sessionBefore = client.sessionCookie();

        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        assertThat(client.sessionCookie()).isNotNull().isNotEqualTo(sessionBefore);
        client.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.username").value(USERNAME));
        // The pre-login token no longer works...
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout")
                        .header("X-CSRF-TOKEN", tokenBefore))
                .andExpect(status().isForbidden());
        // ...a freshly fetched one does.
        client.logout().andExpect(status().isOk());
        client.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void secondLoginEndsTheFirstSession() throws Exception {
        ApiSession first = client();
        first.login(USERNAME, PASSWORD).andExpect(status().isOk());
        first.get("/api/hello").andExpect(status().isOk());

        ApiSession second = client();
        second.login(USERNAME, PASSWORD).andExpect(status().isOk());

        first.get("/api/hello").andExpect(status().isUnauthorized());
        second.get("/api/hello").andExpect(status().isOk());
    }

    @Test
    void logoutDeletesTheSessionSoTheOldCookieCannotBeReplayed() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());
        String cookie = client.sessionCookie();

        client.logout().andExpect(status().isOk());

        ApiSession replay = client();
        replay.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/hello")
                        .cookie(new jakarta.servlet.http.Cookie(ApiSession.SESSION_COOKIE, cookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutOverHttpsSendsClearSiteData() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());
        client.fetchCsrfToken();
        // Build the logout by hand to mark it secure.
        String token = client.fetchCsrfToken();
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout")
                        .secure(true)
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Clear-Site-Data", "\"cache\", \"storage\""));
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        client().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/csrf")
                        .secure(true))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.API_CONTENT_SECURITY_POLICY))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().exists(RequestLoggingContextFilter.CORRELATION_HEADER));
    }
}
