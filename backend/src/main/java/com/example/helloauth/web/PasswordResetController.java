package com.example.helloauth.web;

import com.example.helloauth.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Always returns a generic success (enumeration resistance, Story 6). */
    @PostMapping("/request")
    public ResponseEntity<Dtos.MessageResponse> request(@Valid @RequestBody Dtos.PasswordResetRequest req) {
        passwordResetService.requestReset(req.email(), "http://localhost:3000/reset-password");
        return ResponseEntity.ok(new Dtos.MessageResponse(
                "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Dtos.MessageResponse> confirm(@Valid @RequestBody Dtos.PasswordResetConfirm req) {
        passwordResetService.confirmReset(req.token(), req.password());
        return ResponseEntity.ok(new Dtos.MessageResponse("Password has been reset."));
    }
}
