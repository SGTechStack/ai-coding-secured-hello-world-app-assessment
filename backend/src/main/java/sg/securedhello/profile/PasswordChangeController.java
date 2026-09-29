package sg.securedhello.profile;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.session.MinimalSessionStrategy;
import sg.securedhello.user.SignedInUser;

/**
 * {@code PATCH /api/profile/password}: self-service change and forced-change completion, always with the current
 * password (ADR-008). Answers 204; the session survives with a new id and a new CSRF token, which the SPA fetches
 * again. Refusals: 400 {@code VALIDATION_FAILED} for a wrong current password or a malformed body, 400
 * {@code PASSWORD_REJECTED} with its {@code rule} for a policy rejection. The per-source budget runs before any of
 * this (R-RL-001).
 */
@RestController
public class PasswordChangeController {

    /** The request body. Both members are required; no other member is read. */
    public record PasswordChangeRequest(@NotNull String currentPassword, @NotNull String newPassword) {
    }

    private final PasswordChange passwordChange;
    private final MinimalSessionStrategy rotation;
    private final ProblemDetailWriter writer;

    public PasswordChangeController(PasswordChange passwordChange, MinimalSessionStrategy rotation,
            ProblemDetailWriter writer) {
        this.passwordChange = passwordChange;
        this.rotation = rotation;
        this.writer = writer;
    }

    @PatchMapping("/api/profile/password")
    public ResponseEntity<Void> change(Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        SignedInUser user = (SignedInUser) authentication.getPrincipal();
        passwordChange.change(user.id(), body.currentPassword(), body.newPassword(), request.getSession().getId());
        // Step 5, after the commit: a new id for the surviving session, without re-stamping AUTH_INSTANT (ADR-038).
        rotation.onAuthentication(authentication, request, response);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PasswordChange.CurrentPasswordMismatchException.class)
    void currentPasswordMismatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.VALIDATION_FAILED);
    }
}
