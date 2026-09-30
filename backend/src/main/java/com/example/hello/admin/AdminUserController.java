package com.example.hello.admin;

import com.example.hello.user.UserSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user management. The URL rule in {@code SecurityConfig} and the method rule here both
 * require ROLE_ADMIN (defence in depth).
 */
@RestController
@RequestMapping(path = "/api/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @GetMapping
  public List<UserSummary> listUsers() {
    return adminUserService.listUsers();
  }

  @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  public UserSummary updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request, Authentication actor) {
    return adminUserService.setEnabled(actor.getName(), id, request.enabled());
  }

  @PatchMapping(path = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
  public UserSummary updateRole(
      @PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request, Authentication actor) {
    return adminUserService.changeRole(actor.getName(), id, request.role());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable UUID id, Authentication actor) {
    adminUserService.deleteUser(actor.getName(), id);
  }
}
