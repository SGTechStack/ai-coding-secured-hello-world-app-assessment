package sg.securedhello.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import sg.securedhello.user.SignedInUser;

/**
 * The admin user routes. {@code GET /api/admin/users} and {@code GET /api/admin/users/{uuid}}: an administrator
 * holding the factor, of any age within the session, reads the user list or one user (PRD Story 8; ADR-021).
 * {@code PUT /api/admin/users/{uuid}/enabled}: with a factor from the last 10 minutes, enables or disables another
 * account through the guarded {@link AdminActions} (PRD Story 9; ADR-048). Paths are mapped here, in the controller
 * (REJ-024). An unknown UUID is a 404, which the envelope renders as {@code ACCESS_DENIED}, like any other route with
 * nothing behind it (ADR-043). No repository is reached from here (T-ADM-014).
 */
@RestController
public class AdminUserController {

    private final AdminUsers users;
    private final AdminActions actions;

    public AdminUserController(AdminUsers users, AdminActions actions) {
        this.users = users;
        this.actions = actions;
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserView> list(@AuthenticationPrincipal SignedInUser admin) {
        return users.list(admin.id());
    }

    @GetMapping("/api/admin/users/{id}")
    public AdminUserView find(@AuthenticationPrincipal SignedInUser admin, @PathVariable UUID id) {
        return users.find(admin.id(), id).orElseThrow(AdminUserController::notFound);
    }

    @PutMapping("/api/admin/users/{id}/enabled")
    public AdminUserView setEnabled(@AuthenticationPrincipal SignedInUser admin, @PathVariable UUID id,
            @Valid @RequestBody EnabledRequest body) {
        return actions.setEnabled(admin.id(), id, body.enabled()).orElseThrow(AdminUserController::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
