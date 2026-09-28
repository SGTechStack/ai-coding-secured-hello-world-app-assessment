package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
