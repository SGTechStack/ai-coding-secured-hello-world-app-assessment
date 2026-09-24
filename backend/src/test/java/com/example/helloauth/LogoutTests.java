package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ticket-10 coverage at the HTTP seam: {@code POST /api/auth/logout} must
 * truly end the session — the {@code SPRING_SESSION} row is deleted, the
 * session cookie is cleared, and a replayed pre-logout cookie is rejected
 * (401). Logout is state-changing so CSRF is required; the token itself is
 * cleared too, which is why the SPA re-bootstraps afterwards. Tests drive the
 * real path exactly like the SPA does (ticket 05 strategy) through the
 * shared {@link ApiTestSupport} fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void logoutReturns200DeletesSessionRowAndClearsCookie() throws Exception {
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);
        assertThat(sessionRowCount(session.getValue())).isEqualTo(1);

        MvcResult result = logout(session)
            .andExpect(status().isOk())
            .andReturn();

        // Client-visible side: the SESSION cookie is expired. Assert on the
        // raw Set-Cookie headers — logout emits several clearing cookies for
        // the same name (CookieClearingLogoutHandler + Spring Session's own
        // expiry), and MockMvc's getCookie() skips null-valued entries.
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
            .anyMatch(header -> header.startsWith("SESSION=")
                && header.contains("Max-Age=0"));

        // Server side: the row is gone — "true invalidation", not just a
        // dropped cookie.
        assertThat(sessionRowCount(session.getValue())).isZero();
    }

    @Test
    void replayedPreLogoutCookieIsRejected() throws Exception {
        // A cookie captured before logout resolves to no session afterwards:
        // protected endpoints must answer 401, not a stale identity.
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);
        logout(session).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hello").cookie(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        // State-changing endpoint — a tokenless POST is rejected before the
        // logout handler runs, and the session must survive untouched.
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").cookie(session))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/hello").cookie(session))
            .andExpect(status().isOk());
        assertThat(sessionRowCount(session.getValue())).isEqualTo(1);
    }

    @Test
    void logoutClearsCsrfToken() throws Exception {
        // CsrfLogoutHandler clears the token on logout — the client observes
        // an expired XSRF-TOKEN cookie and must re-bootstrap before its next
        // mutation (the SPA calls GET /api/auth/csrf again).
        seedUser("alice", "alice@example.com");
        Cookie session = loginSession("alice", VALID_PASSWORD);

        MvcResult result = logout(session)
            .andExpect(status().isOk())
            .andReturn();

        Cookie clearedCsrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(clearedCsrf, "logout must clear the CSRF token cookie");
        assertThat(clearedCsrf.getMaxAge()).isZero();
        assertThat(clearedCsrf.getValue()).isEmpty();
    }

    @Test
    void anonymousLogoutIsIdempotent() throws Exception {
        // No session to kill — still 200. Logout reveals nothing sensitive.
        Cookie csrf = csrfToken();
        mockMvc.perform(post("/api/auth/logout")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * POST logout the way the SPA does: session cookie plus a *fresh* CSRF
     * token (login's CsrfAuthenticationStrategy cleared the previous one).
     */
    private ResultActions logout(Cookie session) throws Exception {
        Cookie csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/logout")
            .cookie(session, csrf)
            .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    /**
     * The SESSION cookie carries {@code Base64(session_id)}; the
     * {@code SPRING_SESSION.SESSION_ID} column stores it decoded.
     */
    private int sessionRowCount(String sessionCookieValue) {
        String sessionId = new String(
            java.util.Base64.getDecoder().decode(sessionCookieValue),
            java.nio.charset.StandardCharsets.UTF_8);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
            Integer.class, sessionId);
        return count == null ? 0 : count;
    }
}
