package com.example.demo_app.admin;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin module's user management API. The filter chain lets only {@code ROLE_ADMIN} in
 * (a {@code USER} gets {@code 403}, anonymous {@code 401}), and {@link AdminUserService} checks the
 * role again.
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
}
