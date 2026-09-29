package hello.desk.web;

import hello.desk.auth.AdminService;
import hello.desk.user.PublicUser;
import hello.desk.web.Payloads.EnabledRequest;
import hello.desk.web.Payloads.RoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<PublicUser> list() {
        return adminService.list();
    }

    @PatchMapping("/{id}/enabled")
    public PublicUser setEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody EnabledRequest request,
            Authentication authentication) {
        return adminService.setEnabled(id, request.enabled(), authentication.getName());
    }

    @PatchMapping("/{id}/role")
    public PublicUser setRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        return adminService.setRole(id, request.role(), authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        adminService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
