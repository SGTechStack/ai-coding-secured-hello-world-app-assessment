package com.example.demo_app.admin;

import com.example.demo_app.user.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin module's user management API. The filter chain lets only {@code ROLE_ADMIN} in
 * (a {@code USER} gets {@code 403}, anonymous {@code 401}) and requires the CSRF token on every
 * change, and {@link AdminUserService} checks the role again. The signed-in admin is passed on as
 * the actor, so the service can refuse actions on their own account and audit who did what.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

  private final AdminUserService service;

  AdminUserController(AdminUserService service) {
    this.service = service;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  List<AdminUserView> listUsers() {
    return service.listUsers();
  }

  @PatchMapping(
      value = "/{id}/status",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  AdminUserView setStatus(
      @PathVariable long id,
      @Valid @RequestBody StatusChangeRequest body,
      @AuthenticationPrincipal AccountUserDetails admin,
      HttpServletRequest request) {
    return service.setEnabled(id, body.enabled(), admin.getUsername(), request);
  }

  @PatchMapping(
      value = "/{id}/role",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  AdminUserView setRole(
      @PathVariable long id,
      @Valid @RequestBody RoleChangeRequest body,
      @AuthenticationPrincipal AccountUserDetails admin,
      HttpServletRequest request) {
    return service.setRole(id, body.toRole(), admin.getUsername(), request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> deleteUser(
      @PathVariable long id,
      @AuthenticationPrincipal AccountUserDetails admin,
      HttpServletRequest request) {
    service.deleteUser(id, admin.getUsername(), request);
    return ResponseEntity.noContent().build();
  }
}
