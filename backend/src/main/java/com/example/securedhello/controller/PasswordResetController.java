package com.example.securedhello.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.PasswordResetRequestRequest;
import com.example.securedhello.service.PasswordResetService;

/**
 * Public password-reset endpoints. The request endpoint always returns the
 * same generic success so account existence cannot be inferred (Enumeration
 * Resistance). Confirmation (issue 08) is added on the same controller.
 */
@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private static final Map<String, String> GENERIC_SUCCESS =
            Map.of("message", "If that email is registered, a reset link has been sent");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public Map<String, String> requestReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        return GENERIC_SUCCESS;
    }
}
