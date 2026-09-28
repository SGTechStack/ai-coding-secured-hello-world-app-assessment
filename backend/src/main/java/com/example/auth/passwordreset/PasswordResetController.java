package com.example.auth.passwordreset;

import com.example.auth.security.SessionTerminationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Both endpoints are permitted anonymously in {@code SecurityConfig} -- a reset request comes before login. */
@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final SessionTerminationService sessionTerminationService;

    public PasswordResetController(
            PasswordResetService passwordResetService, SessionTerminationService sessionTerminationService) {
        this.passwordResetService = passwordResetService;
        this.sessionTerminationService = sessionTerminationService;
    }

    @PostMapping("/api/password-reset/request")
    public ResponseEntity<Void> requestReset(@RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(@RequestBody PasswordResetConfirmRequest request) {
        String username = passwordResetService.confirmReset(request.token(), request.newPassword());
        // Called only after confirmReset's transaction has returned/committed --
        // see SessionTerminationService for why.
        sessionTerminationService.expireSessionsFor(username);
        return ResponseEntity.ok().build();
    }
}
