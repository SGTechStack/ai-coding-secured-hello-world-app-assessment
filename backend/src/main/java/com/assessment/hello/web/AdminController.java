package com.assessment.hello.web;

import com.assessment.hello.dto.MessageResponse;
import com.assessment.hello.dto.RoleChangeRequest;
import com.assessment.hello.dto.UserView;
import com.assessment.hello.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<UserView> listUsers() {
        return adminService.listUsers();
    }

    @PutMapping("/users/{id}/status")
    public UserView setStatus(@PathVariable UUID id,
                              @RequestParam boolean enabled,
                              Authentication authentication) {
        return adminService.setEnabled(authentication.getName(), id, enabled);
    }

    @PutMapping("/users/{id}/role")
    public UserView changeRole(@PathVariable UUID id,
                               @Valid @RequestBody RoleChangeRequest request,
                               Authentication authentication) {
        return adminService.changeRole(authentication.getName(), id, request.role());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable UUID id,
                                                      Authentication authentication) {
        adminService.deleteUser(authentication.getName(), id);
        return ResponseEntity.ok(new MessageResponse("User deleted"));
    }
}
