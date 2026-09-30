package sg.securedhello.admin;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import sg.securedhello.audit.UnlockReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.SignedInUser;

/**
 * The admin credential routes, each needing a factor from the last 10 minutes (ADR-021). Admins issue single-use
 * tokens, never passwords (ADR-006):
 * <ul>
 *   <li>{@code POST /api/admin/users} invites an account through {@link AdminInvitations} and answers 201 with its
 *       activation token. A taken or tombstoned identifier is 400 {@code USER_EXISTS}; a malformed one 400
 *       {@code VALIDATION_FAILED}.</li>
 *   <li>{@code POST /api/admin/users/{uuid}/password-reset} returns a reset token for any activated, enabled account,
 *       the admin's own included, through the guarded {@link AdminActions}. It ends the account's sessions and
 *       clears nothing. The user redeems it at {@code /api/password-reset/confirm}.</li>
 *   <li>{@code POST /api/admin/users/{uuid}/unlock} takes {@code {reason}}, a closed {@link UnlockReason}, and clears
 *       the password lockout and the tier-1 factor lock of another account (REJ-028; REJ-072).</li>
 * </ul>
 * A token response is sent with {@code Cache-Control: no-store}, and the token is never logged (R-FE-007). An unknown
 * UUID is a 404, which the envelope renders as {@code ACCESS_DENIED} (ADR-043). No repository is reached from here.
 */
@RestController
public class AdminCredentialController {

    /** The invite body. No password member: the invitee sets their own (ADR-006). No other member is read. */
    public record InviteRequest(@NotNull String username, @NotNull String email,
            @NotNull @Pattern(regexp = "USER|ADMIN") String role) {
    }

    /** The unlock body: the reason, one of the closed enum's values. */
    public record UnlockRequest(@NotNull UnlockReason reason) {
    }

    private final AdminInvitations invitations;
    private final AdminActions actions;
    private final ProblemDetailWriter writer;

    public AdminCredentialController(AdminInvitations invitations, AdminActions actions, ProblemDetailWriter writer) {
        this.invitations = invitations;
        this.actions = actions;
        this.writer = writer;
    }

    @PostMapping("/api/admin/users")
    public ResponseEntity<IssuedToken> invite(@AuthenticationPrincipal SignedInUser admin,
            @Valid @RequestBody InviteRequest body) {
        IssuedToken issued = invitations.invite(admin.id(), body.username(), body.email(), body.role());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(issued);
    }

    @PostMapping("/api/admin/users/{id}/password-reset")
    public ResponseEntity<IssuedToken> issuePasswordReset(@AuthenticationPrincipal SignedInUser admin,
            @PathVariable UUID id) {
        IssuedToken issued = actions.issuePasswordReset(admin.id(), id).orElseThrow(AdminCredentialController::notFound);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issued);
    }

    @PostMapping("/api/admin/users/{id}/unlock")
    public AdminUserView unlock(@AuthenticationPrincipal SignedInUser admin, @PathVariable UUID id,
            @Valid @RequestBody UnlockRequest body) {
        return actions.unlock(admin.id(), id, body.reason()).orElseThrow(AdminCredentialController::notFound);
    }

    @ExceptionHandler({AdminInvitations.InvalidIdentifierException.class, NotResettableException.class})
    void invalid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(AdminInvitations.UserExistsException.class)
    void userExists(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.USER_EXISTS);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
