package com.eitri.passwordreset;

import com.eitri.audit.AuditAccount;
import com.eitri.audit.AuditLogger;
import com.eitri.auth.AccountService;
import com.eitri.auth.AccountView;
import com.eitri.auth.PasswordPolicy;
import com.eitri.config.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/auth/password-reset")
class PasswordResetController {

    private static final ApiError REQUEST_ACCEPTED =
            new ApiError("If that email is registered, a password reset link has been sent.");
    private static final ApiError INVALID_TOKEN = new ApiError("Invalid or expired reset token");

    private final PasswordResetService passwordReset;
    private final AccountService accounts;
    private final AuditLogger auditLogger;

    PasswordResetController(PasswordResetService passwordReset, AccountService accounts, AuditLogger auditLogger) {
        this.passwordReset = passwordReset;
        this.accounts = accounts;
        this.auditLogger = auditLogger;
    }

    /** Always the same 202, even for a missing or malformed email, so account existence can't be inferred. */
    @PostMapping("/request")
    ResponseEntity<ApiError> request(@RequestBody(required = false) Map<String, Object> body) {
        if (body != null && body.get("email") instanceof String email && !email.isBlank()) {
            passwordReset.request(email);
        }
        return accepted();
    }

    /**
     * A reset request whose body isn't even JSON still gets the generic 202, like any other unusable
     * email; other endpoints here keep the generic 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpServletRequest request) {
        if (request.getRequestURI().endsWith("/password-reset/request")) {
            return accepted();
        }
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST));
    }

    private static ResponseEntity<ApiError> accepted() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(REQUEST_ACCEPTED);
    }

    /** Policy first (a weak password never consumes the token), then the token, then all sessions end. */
    @PostMapping("/confirm")
    ResponseEntity<ApiError> confirm(@RequestBody ResetConfirmation confirmation) {
        Optional<String> weak = PasswordPolicy.violation(confirmation.newPassword());
        if (weak.isPresent()) {
            return ResponseEntity.badRequest().body(new ApiError(weak.get()));
        }
        if (confirmation.token() == null || confirmation.token().isEmpty()) {
            return ResponseEntity.badRequest().body(INVALID_TOKEN);
        }
        Optional<AccountView> reset = passwordReset.confirm(confirmation.token(), confirmation.newPassword());
        if (reset.isEmpty()) {
            return ResponseEntity.badRequest().body(INVALID_TOKEN);
        }
        accounts.endAllSessions(reset.get().id());
        auditLogger.passwordResetCompleted(new AuditAccount(reset.get().id(), reset.get().username()));
        return ResponseEntity.noContent().build();
    }

    record ResetConfirmation(String token, String newPassword) {}
}
