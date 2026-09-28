package com.example.auth.admin;

import com.example.auth.security.SessionTerminationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/admin/**} is gated by {@code .hasRole("ADMIN")} in {@code SecurityConfig}; a
 * non-admin never reaches here.
 *
 * <p>Disabling, demoting/promoting or deleting a target user expires their existing sessions via
 * {@link SessionTerminationService} -- called only after the corresponding {@code
 * AdminUserService} method (and its transaction) has returned, same rationale as {@code
 * PasswordResetController}: an admin action must take effect immediately, not just block the
 * target's next login attempt.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final SessionTerminationService sessionTerminationService;

    public AdminUserController(AdminUserService adminUserService, SessionTerminationService sessionTerminationService) {
        this.adminUserService = adminUserService;
        this.sessionTerminationService = sessionTerminationService;
    }

    @GetMapping
    public List<AdminUserView> listUsers() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/{id}/status")
    public AdminUserView updateStatus(
            @PathVariable Long id, @RequestBody UpdateStatusRequest request, Authentication authentication) {
        AdminUserView view = adminUserService.setEnabled(id, request.enabled(), authentication.getName());
        if (!request.enabled()) {
            sessionTerminationService.expireSessionsFor(view.username());
        }
        return view;
    }

    @PatchMapping("/{id}/role")
    public AdminUserView updateRole(
            @PathVariable Long id, @RequestBody UpdateRoleRequest request, Authentication authentication) {
        AdminUserView view = adminUserService.setRole(id, request.role(), authentication.getName());
        sessionTerminationService.expireSessionsFor(view.username());
        return view;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        String deletedUsername = adminUserService.deleteUser(id, authentication.getName());
        sessionTerminationService.expireSessionsFor(deletedUsername);
        return ResponseEntity.noContent().build();
    }
}
