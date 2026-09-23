package com.assessment.securedhelloworld.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forces resolution of the deferred {@link CsrfToken}, which is what
 * actually triggers {@code CookieCsrfTokenRepository} to write the
 * XSRF-TOKEN cookie on the response. Spring Security only writes this
 * cookie once the token has been read by something; without an endpoint
 * like this, an SPA has no reliable way to prime the cookie before its
 * first mutating request.
 */
@RestController
public class CsrfTokenController {

    @GetMapping("/api/csrf")
    public ResponseEntity<Void> getCsrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // Calling getToken() forces resolution of the deferred token,
            // which is what causes CookieCsrfTokenRepository to write the
            // XSRF-TOKEN cookie on this response.
            token.getToken();
        }
        return ResponseEntity.ok().build();
    }
}
