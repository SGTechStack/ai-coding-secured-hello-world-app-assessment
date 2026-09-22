package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the SPA fetch a CSRF token up front. Spring Security's
 * CookieCsrfTokenRepository already exposes the token via the
 * XSRF-TOKEN cookie on any request once this endpoint has been hit once,
 * but exposing it explicitly here makes the frontend's job (read the
 * cookie, send it back as a header on the next state-changing request)
 * unambiguous rather than relying on incidental cookie-setting.
 */
@RestController
public class CsrfTokenController {

    @GetMapping("/api/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }
}
