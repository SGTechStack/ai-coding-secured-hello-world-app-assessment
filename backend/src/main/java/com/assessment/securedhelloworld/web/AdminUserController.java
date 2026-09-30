package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.security.AppUserDetails;
import com.assessment.securedhelloworld.service.AdminUserService;
import com.assessment.securedhelloworld.web.dto.AdminUserView;
import com.assessment.securedhelloworld.web.dto.RoleChangeRequest;
import com.assessment.securedhelloworld.web.dto.StatusChangeRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code /api/admin/**} is already restricted to {@code hasRole("ADMIN")} by SecurityConfig, and
 * further gated by {@code AdminAccessConfig}'s interceptor when the caller still has a forced
 * password change pending (PRD Story 12 / IM8 ac-6). Every method here is a thin pass-through to
 * {@link AdminUserService}, which owns the self-action guard and audit logging.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public List<AdminUserView> listUsers() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserView setStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                    @AuthenticationPrincipal AppUserDetails principal) {
        return adminUserService.setEnabled(id, request.enabled(), principal);
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserView changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request,
                                     @AuthenticationPrincipal AppUserDetails principal) {
        return adminUserService.changeRole(id, request.role(), principal);
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        adminUserService.deleteUser(id, principal);
    }
}
