package com.example.hello.reset;

import com.example.hello.auth.AuthRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/request")
    Map<String, String> request(@Valid @RequestBody ResetRequest request) {
        service.request(request.email());
        return Map.of("message", "If an account matches that email, a password reset link has been sent.");
    }

    @PostMapping("/confirm")
    Map<String, String> confirm(@Valid @RequestBody ResetConfirmation request) {
        service.confirm(request.token(), request.password());
        return Map.of("message", "Your password has been updated. Please sign in again.");
    }

    public record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {
        public ResetRequest { email = AuthRequests.normalize(email); }
    }

    public record ResetConfirmation(@NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "ResetConfirmation[redacted]"; }
    }
}
