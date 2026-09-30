package com.assessment.auth.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CSRF bootstrap endpoint (spec.md S4).
 *
 * <p>Because the repository is session-bound and {@code CookieCsrfTokenRepository} is strictly
 * prohibited (Std:238), <strong>no {@code XSRF-TOKEN} cookie exists</strong> — this endpoint is the
 * only way a client can obtain a token, which makes the bootstrap {@code GET /csrf} →
 * {@code POST /auth/login} → {@code GET /currentUser} mandatory rather than conventional.
 *
 * <p>{@code no-store} is set explicitly. The source recipe's controller omits it, and Std:238 and
 * :443 both require it: a cached CSRF token is a CSRF token shared between users.
 */
@RestController
public class CsrfController {

  /**
   * @param token the token value to echo in {@code X-CSRF-TOKEN}
   * @param headerName always {@code X-CSRF-TOKEN}; returned so the SPA need not hard-code it
   */
  public record CsrfTokenResponse(String token, String headerName, String parameterName) {}

  @GetMapping("${api.base-path}/csrf")
  public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new CsrfTokenResponse(
                token.getToken(), token.getHeaderName(), token.getParameterName()));
  }
}
