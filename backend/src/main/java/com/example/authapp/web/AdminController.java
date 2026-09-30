package com.example.authapp.web;

import com.example.authapp.service.AdminService;
import com.example.authapp.web.Dtos.EnabledRequest;
import com.example.authapp.web.Dtos.RoleRequest;
import com.example.authapp.web.Dtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Access to /api/admin/** is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    public List<UserResponse> list() {
        return admin.listUsers().stream().map(UserResponse::of).toList();
    }

    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest body,
            Authentication auth) {
        return UserResponse.of(admin.setEnabled(auth.getName(), id, body.enabled()));
    }

    @PatchMapping("/{id}/role")
    public UserResponse setRole(@PathVariable Long id, @Valid @RequestBody RoleRequest body, Authentication auth) {
        return UserResponse.of(admin.setRole(auth.getName(), id, body.role()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        admin.delete(auth.getName(), id);
    }
}
