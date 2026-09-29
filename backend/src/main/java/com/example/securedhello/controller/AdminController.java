package com.example.securedhello.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.AdminUserResponse;
import com.example.securedhello.service.AdminUserService;

/**
 * Admin account-management endpoints. Access is restricted to ADMIN by the
 * security filter chain ({@code /api/admin/**} requires role ADMIN), enforced
 * server-side. Password hashes are never returned.
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
}
