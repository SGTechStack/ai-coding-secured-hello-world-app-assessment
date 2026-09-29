package com.example.auth.admin;

import java.util.List;
import java.util.UUID;

import com.example.auth.user.UserPrincipal;
import com.example.auth.user.UserResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user-management endpoints. Access is gated to ROLE_ADMIN by the
 * SecurityConfig filter chain (anonymous → 401, non-admin → 403). The acting
 * admin is resolved from the authenticated principal and never from the request.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    List<UserResponse> listUsers() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/{id}/status")
    UserResponse setStatus(@AuthenticationPrincipal UserPrincipal actor,
                           @PathVariable UUID id,
                           @Valid @RequestBody StatusUpdateRequest req) {
        return adminUserService.setEnabled(actor.getUserId(), id, req.enabled());
    }

    @PatchMapping("/{id}/role")
    UserResponse changeRole(@AuthenticationPrincipal UserPrincipal actor,
                            @PathVariable UUID id,
                            @Valid @RequestBody RoleUpdateRequest req) {
        return adminUserService.changeRole(actor.getUserId(), id, req.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(@AuthenticationPrincipal UserPrincipal actor,
                    @PathVariable UUID id) {
        adminUserService.deleteUser(actor.getUserId(), id);
    }
}
