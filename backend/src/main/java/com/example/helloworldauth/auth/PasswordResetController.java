package com.example.helloworldauth.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private static final Map<String, String> GENERIC_RESPONSE = Map.of(
        "message", "If an account exists for that email, a reset link has been sent.");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Always returns the same generic 200 response regardless of whether the
     * email is registered (enumeration resistance).
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok(GENERIC_RESPONSE);
    }

    /**
     * Completes a reset with a valid, unexpired, unused token and a
     * policy-compliant new password. On success the password is updated, the
     * token is consumed (single-use), and the user's existing sessions are
     * invalidated. Invalid/expired/used tokens are rejected with 400 via
     * {@link InvalidResetTokenException} (mapped in the global handler); a new
     * password shorter than the policy minimum is a 400 bean-validation error.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmReset(
        @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Your password has been reset."));
    }
}
