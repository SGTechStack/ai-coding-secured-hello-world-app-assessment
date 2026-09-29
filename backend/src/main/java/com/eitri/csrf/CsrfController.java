package com.eitri.csrf;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands the SPA the session's CSRF synchronizer token. Reading the token saves it to the session,
 * so an anonymous call starts one.
 */
@RestController
class CsrfController {

    @GetMapping("${app.api.base-path}/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    record CsrfResponse(String headerName, String token) {}
}
