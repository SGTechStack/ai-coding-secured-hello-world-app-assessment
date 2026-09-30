package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.service.PasswordResetService;
import com.assessment.securedhelloworld.web.dto.MessageResponse;
import com.assessment.securedhelloworld.web.dto.PasswordResetConfirmRequest;
import com.assessment.securedhelloworld.web.dto.PasswordResetRequestRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both endpoints are {@code permitAll} (SecurityConfig, ticket 01) since a visitor without a
 * session must be able to use them. {@code /request} always returns the SAME generic 200 body
 * regardless of what {@link PasswordResetService#requestReset} found internally — that uniformity,
 * not any behavior inside the service, is what makes the endpoint enumeration-resistant (PRD Story
 * 6), so no exception or branch from the service is allowed to change this response.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private static final MessageResponse GENERIC_REQUEST_RESPONSE =
            new MessageResponse("If that email is registered, a reset link has been sent");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<MessageResponse> request(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok(GENERIC_REQUEST_RESPONSE);
    }

    @PostMapping("/confirm")
    public ResponseEntity<MessageResponse> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password has been reset"));
    }
}
