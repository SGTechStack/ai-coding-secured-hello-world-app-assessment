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
}
