package com.example.helloworldauth.auth;

import com.example.helloworldauth.config.InMemoryIndexedSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ends an authenticated session. Because everything but {@code /api/ping},
 * {@code /api/register} and {@code /api/login} requires authentication (see
 * {@link com.example.helloworldauth.config.SecurityConfig}), an unauthenticated
 * caller is rejected with 401 before this handler runs, and CSRF is enforced on
 * this state-changing POST by the cookie CSRF token repository.
 *
 * <p>Logout invalidates the server-side {@link HttpSession} so a session cookie
 * captured before logout can no longer authenticate, clears the security context,
 * and expires the JSESSIONID cookie in the browser.
 */
@RestController
@RequestMapping("/api")
public class LogoutController {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final InMemoryIndexedSessionRepository indexedSessions;

    public LogoutController(InMemoryIndexedSessionRepository indexedSessions) {
        this.indexedSessions = indexedSessions;
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null ? authentication.getName() : "unknown";

        // Invalidate the server-side session so the old id can never re-authenticate.
        HttpSession session = request.getSession(false);
        if (session != null) {
            // Drop the principal-name-indexed mirror for this session too.
            indexedSessions.deleteById(session.getId());
            session.invalidate();
        }

        // Clear the authentication from the current thread's context.
        SecurityContextHolder.clearContext();

        // Expire the session cookie in the browser.
        Cookie expired = new Cookie("JSESSIONID", "");
        expired.setPath("/");
        expired.setMaxAge(0);
        expired.setHttpOnly(true);
        response.addCookie(expired);

        audit.info("logout username={}", actor);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
