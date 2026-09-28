package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin module — user administration endpoints under {@code /api/admin/**}.
 *
 * <p>Authorization is enforced server-side by Spring Security's
 * {@code /api/admin/**} matcher ({@code hasRole("ADMIN")} in SecurityConfig),
 * never trusted from client state. An unauthenticated caller is rejected with
 * 401 by the authentication entry point; an authenticated non-admin is rejected
 * with 403 by the access-denied handler before this handler is reached.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    /**
     * Lists every account. Returns a DTO projection ({@link AdminUserResponse})
     * that excludes the password hash — the entity is never serialized directly.
     */
    @GetMapping("/users")
    public List<AdminUserResponse> listUsers(Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        AUDIT.info("admin user-list accessed actor={}", actor);
        return users.findAll().stream()
            .map(AdminUserResponse::from)
            .toList();
    }

    /**
     * Enables or disables a target account (Story 9). Flipping {@code enabled}
     * off suspends the account without deleting its data: a disabled user can no
     * longer log in (enforced by {@code AppUserDetailsService}/{@code LoginService}).
     *
     * <p>State-changing PATCH under {@code /api/admin/**}, so it inherits the
     * ADMIN role guard and CSRF protection. The acting admin is taken from the
     * security context — never from the request body. SELF-ACTION GUARD: an admin
     * may not toggle their own account (they could disable themselves and lose
     * access), so a target whose username equals the authenticated principal's is
     * rejected with 400 and the account is left unchanged.
     */
    @PatchMapping("/users/{id}/enabled")
    public AdminUserResponse setEnabled(@PathVariable UUID id,
                                        @Valid @RequestBody SetEnabledRequest request,
                                        Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";

        User target = users.findById(id)
            .orElseThrow(() -> new AdminUserNotFoundException("no account with id " + id));

        if (target.getUsername().equals(actor)) {
            AUDIT.info("admin self-action rejected actor={} target={} action=set-enabled",
                actor, target.getUsername());
            throw new SelfActionForbiddenException("an admin cannot change their own account status");
        }

        boolean enabled = request.enabled();
        target.setEnabled(enabled);
        users.save(target);
        AUDIT.info("admin account {} actor={} target={}",
            enabled ? "enable" : "disable", actor, target.getUsername());

        return AdminUserResponse.from(target);
    }
}
