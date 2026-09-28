package com.assessment.securedhelloworld.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Self-service password change for an authenticated user
 * ({@code POST /api/auth/change-password}); the only endpoint (besides
 * logout and CSRF priming) reachable while
 * {@code forcePasswordChange} is set.
 */
@RestController
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.changePassword(principal, request);
        return ResponseEntity.ok(Map.of("message", "Password has been changed"));
    }
}
