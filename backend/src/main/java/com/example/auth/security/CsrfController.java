package com.example.auth.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/auth/csrf}: hands the SPA the session-bound CSRF token (ADR-0003). The token lives
 * only in the server-side session -- never in a JS-readable cookie (App-Standards UAC 3.1) -- and
 * the value returned is XOR-masked per request (BREACH protection), so it is never cacheable.
 * The SPA keeps it in memory and sends it as {@code X-CSRF-TOKEN}; it must refetch after login and
 * logout, since both rotate the token.
 */
@RestController
public class CsrfController {

    public record CsrfTokenResponse(String headerName, String token) {}

    @GetMapping("/api/auth/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }
}
