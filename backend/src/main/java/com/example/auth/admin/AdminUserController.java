package com.example.auth.admin;

import jakarta.validation.Valid;
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
 * non-admin never reaches here. Session termination for affected users happens inside {@link
 * AdminUserService}, after its transaction commits.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserView> listUsers() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/{id}/status")
    public AdminUserView updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request, Authentication authentication) {
        return adminUserService.setEnabled(id, request.enabled(), authentication.getName());
    }

    @PatchMapping("/{id}/role")
    public AdminUserView updateRole(
            @PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request, Authentication authentication) {
        return adminUserService.setRole(id, request.role(), authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        adminUserService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
