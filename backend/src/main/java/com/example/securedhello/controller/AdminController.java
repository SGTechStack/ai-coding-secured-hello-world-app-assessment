package com.example.securedhello.controller;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.AdminUserResponse;
import com.example.securedhello.controller.dto.UpdateEnabledRequest;
import com.example.securedhello.controller.dto.UpdateRoleRequest;
import com.example.securedhello.service.AdminUserService;

/**
 * Admin account-management endpoints. Access is restricted to ADMIN by the
 * security filter chain ({@code /api/admin/**} requires role ADMIN). Mutations
 * are subject to the Admin Self-Action Guard enforced in the service layer.
 * Password hashes are never returned.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable Long id,
                                           @Valid @RequestBody UpdateEnabledRequest request,
                                           Principal principal) {
        adminUserService.setEnabled(principal.getName(), id, request.enabled());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Void> changeRole(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRoleRequest request,
                                           Principal principal) {
        adminUserService.changeRole(principal.getName(), id, request.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Principal principal) {
        adminUserService.deleteUser(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
