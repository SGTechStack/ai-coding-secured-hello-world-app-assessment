package com.example.helloauth.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin user-management endpoints under {@code /api/admin/**} — the
 * filter chain gates the whole prefix to {@code ROLE_ADMIN}; self-targeting
 * rejection lives in {@link AdminService} where the acting admin's identity
 * (the principal's username) is explicit. PATCHes return the updated account
 * so the SPA can refresh a row without re-listing.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminService.listUsers();
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest body,
            Authentication authentication) {
        return adminService.setEnabled(
            authentication.getName(), id, body.enabled());
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest body,
            Authentication authentication) {
        return adminService.setRole(authentication.getName(), id, body.role());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
            Authentication authentication) {
        adminService.deleteUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
