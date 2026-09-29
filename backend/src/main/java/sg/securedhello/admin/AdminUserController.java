package sg.securedhello.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import sg.securedhello.user.SignedInUser;

/**
 * {@code GET /api/admin/users} and {@code GET /api/admin/users/{uuid}}: an administrator holding the factor, of any
 * age within the session, reads the user list or one user (PRD Story 8; ADR-021). Paths are mapped here, in the
 * controller (REJ-024). An unknown UUID is a 404, which the envelope renders as {@code ACCESS_DENIED}, like any other
 * route with nothing behind it (ADR-043).
 */
@RestController
public class AdminUserController {

    private final AdminUsers users;

    public AdminUserController(AdminUsers users) {
        this.users = users;
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserView> list(@AuthenticationPrincipal SignedInUser admin) {
        return users.list(admin.id());
    }

    @GetMapping("/api/admin/users/{id}")
    public AdminUserView find(@AuthenticationPrincipal SignedInUser admin, @PathVariable UUID id) {
        return users.find(admin.id(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
