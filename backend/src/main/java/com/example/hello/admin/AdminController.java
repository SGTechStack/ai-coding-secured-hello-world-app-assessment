package com.example.hello.admin;

import com.example.hello.auth.AuthPrincipal;
import com.example.hello.user.Role;
import com.example.hello.user.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @GetMapping List<UserView> users() { return service.list(); }

    @PatchMapping("/{id}/status")
    UserView status(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.status(actor, id, request.enabled());
    }

    @PatchMapping("/{id}/role")
    UserView role(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return service.role(actor, id, request.role());
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable UUID id) { service.delete(actor, id); }

    public record StatusRequest(@NotNull Boolean enabled) {}
    public record RoleRequest(@NotNull Role role) {}
}
