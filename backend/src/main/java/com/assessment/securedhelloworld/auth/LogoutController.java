package com.assessment.securedhelloworld.auth;

import jakarta.servlet.http.HttpServletRequest;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logs the user out: invalidates the current HTTP session and clears the
 * security context. Reachable even while
 * {@code forcePasswordChange} blocks other endpoints.
 */
@RestController
public class LogoutController {

    private static final Logger log = LoggerFactory.getLogger(LogoutController.class);

    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "unknown";

        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        log.info("Logout username={}", LogSanitizer.sanitize(username));
        return ResponseEntity.ok().build();
    }
}
