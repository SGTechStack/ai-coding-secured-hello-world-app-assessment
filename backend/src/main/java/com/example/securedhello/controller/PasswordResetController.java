package com.example.securedhello.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.PasswordResetConfirmRequest;
import com.example.securedhello.controller.dto.PasswordResetRequestRequest;
import com.example.securedhello.service.InvalidResetTokenException;
import com.example.securedhello.service.PasswordResetService;

/**
 * Public password-reset endpoints. The request endpoint always returns the
 * same generic success so account existence cannot be inferred (Enumeration
 * Resistance). The confirm endpoint validates the token and, on success,
 * updates the password and revokes all of the user's Sessions.
 */
@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private static final Map<String, String> GENERIC_SUCCESS =
            Map.of("message", "If that email is registered, a reset link has been sent");
    private static final Map<String, String> GENERIC_INVALID =
            Map.of("error", "Invalid or expired reset token");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public Map<String, String> requestReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        return GENERIC_SUCCESS;
    }

    @PostMapping("/confirm")
    public Map<String, String> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return Map.of("message", "Password has been reset");
    }

    @ExceptionHandler({InvalidResetTokenException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> handleInvalid(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GENERIC_INVALID);
    }
}
