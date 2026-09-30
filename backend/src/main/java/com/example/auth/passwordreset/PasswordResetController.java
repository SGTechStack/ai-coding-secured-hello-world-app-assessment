package com.example.auth.passwordreset;

import com.example.auth.security.ratelimit.RateLimiters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both endpoints are permitted anonymously in {@code SecurityConfig} -- a reset request comes
 * before login. Each is rate-limited per source IP (ADR-0005); the request endpoint is additionally
 * limited per email inside {@link PasswordResetService}, silently.
 */
@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RateLimiters rateLimiters;

    public PasswordResetController(PasswordResetService passwordResetService, RateLimiters rateLimiters) {
        this.passwordResetService = passwordResetService;
        this.rateLimiters = rateLimiters;
    }

    @PostMapping("/api/password-reset/request")
    public ResponseEntity<Void> requestReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        rateLimiters.resetRequestIp().enforce(httpRequest.getRemoteAddr());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(
            @Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        rateLimiters.resetConfirmIp().enforce(httpRequest.getRemoteAddr());
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
