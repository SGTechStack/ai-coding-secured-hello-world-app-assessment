package sg.securedhello.passwordreset;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.security.ratelimit.TooManyRequests;

/**
 * Password reset, both anonymous and both behind their budgets (ADR-010):
 * <ul>
 *   <li>{@code POST /api/password-reset/request} takes {@code {email}} and answers the same empty 202 whether or not
 *       the address names an account (PRD Story 6). A value that cannot be an address is 400
 *       {@code VALIDATION_FAILED}, and an address whose budget is spent is 429 {@code TOO_MANY_REQUESTS}, registered or
 *       not.</li>
 *   <li>{@code POST /api/password-reset/confirm} takes {@code {token, password}} and answers 204. A token that does
 *       not redeem is 400 {@code RESET_TOKEN_INVALID}; a refused password is 400 {@code PASSWORD_REJECTED} with its
 *       {@code rule}, and the token stays redeemable.</li>
 * </ul>
 * No other body member is read: nothing a caller sends shapes the link (REJ-022). Neither creates a session.
 */
@RestController
public class PasswordResetController {

    /** The request body. The address is required; no other member is read. */
    public record ResetRequest(@NotNull String email) {
    }

    /** The redemption body. Both members are required; no other member is read. */
    public record ResetConfirmation(@NotNull String token, @NotNull String password) {
    }

    private final PasswordResetRequests requests;
    private final PasswordResetRedemption redemption;
    private final AuditEmitter audit;
    private final ProblemDetailWriter writer;

    public PasswordResetController(PasswordResetRequests requests, PasswordResetRedemption redemption,
            AuditEmitter audit, ProblemDetailWriter writer) {
        this.requests = requests;
        this.redemption = redemption;
        this.audit = audit;
        this.writer = writer;
    }

    @PostMapping("/api/password-reset/request")
    public ResponseEntity<Void> request(@Valid @RequestBody ResetRequest body) {
        requests.request(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/password-reset/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ResetConfirmation body) {
        redemption.redeem(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PasswordResetRequests.InvalidEmailException.class)
    void invalidEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.VALIDATION_FAILED);
    }

    /** Row 6 is transition-keyed: written on the first refusal after the address was last admitted. */
    @ExceptionHandler(PasswordResetRequests.ThrottledException.class)
    void throttled(PasswordResetRequests.ThrottledException throttled, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (throttled.refusal().breach()) {
            audit.emit(AuditEvent.IDENTIFIER_THROTTLED, AccountContext.identifierThrottled());
        }
        TooManyRequests.write(writer, request, response, throttled.refusal());
    }
}
