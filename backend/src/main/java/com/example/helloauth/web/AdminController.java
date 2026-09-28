package com.example.helloauth.web;

import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import com.example.helloauth.security.AppUserDetails;
import com.example.helloauth.service.ServiceExceptions;
import com.example.helloauth.service.SessionRegistryService;
import com.example.helloauth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final SessionRegistryService sessionRegistry;

    public AdminController(UserService userService, SessionRegistryService sessionRegistry) {
        this.userService = userService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/users")
    public List<Dtos.AdminUserView> listUsers() {
        return userService.listAll().stream().map(Dtos.AdminUserView::from).toList();
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Dtos.AdminUserView> setStatus(@PathVariable UUID id,
                                                        @RequestBody Dtos.StatusChangeRequest req,
                                                        @AuthenticationPrincipal AppUserDetails principal) {
        User actor = userService.requireUser(principal.getId());
        User updated = userService.setEnabled(id, req.enabled(), actor);
        if (!req.enabled()) {
            // A disabled user's active sessions are terminated immediately.
            sessionRegistry.invalidateSessionsForUser(updated.getId());
        }
        return ResponseEntity.ok(Dtos.AdminUserView.from(updated));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Dtos.AdminUserView> changeRole(@PathVariable UUID id,
                                                         @Valid @RequestBody Dtos.RoleChangeRequest req,
                                                         @AuthenticationPrincipal AppUserDetails principal) {
        Role role;
        try {
            role = Role.valueOf(req.role().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceExceptions.ValidationException("Invalid role. Must be USER or ADMIN.");
        }
        User actor = userService.requireUser(principal.getId());
        User updated = userService.changeRole(id, role, actor);
        // Force re-auth so the new authorities take effect on the target's next request.
        sessionRegistry.invalidateSessionsForUser(updated.getId());
        return ResponseEntity.ok(Dtos.AdminUserView.from(updated));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Dtos.MessageResponse> delete(@PathVariable UUID id,
                                                       @AuthenticationPrincipal AppUserDetails principal) {
        User actor = userService.requireUser(principal.getId());
        userService.delete(id, actor);
        sessionRegistry.invalidateSessionsForUser(id);
        return ResponseEntity.ok(new Dtos.MessageResponse("User deleted."));
    }
}
