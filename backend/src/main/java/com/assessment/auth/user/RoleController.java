package com.assessment.auth.user;

import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only role list (story 1.17, matrix row 7).
 *
 * <p>There is deliberately no create, update or delete route: roles are seeded from configuration
 * at startup and Std:407 / :415 make the definitions a persisted, fixed set. Adding a write route
 * here would make the role vocabulary a runtime concern, and the authorization matrix names role
 * strings directly.
 */
@RestController
public class RoleController {

  private final RoleRepository roleRepository;

  public RoleController(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  @GetMapping("${api.base-path}/roles")
  public List<String> roles() {
    return roleRepository.findAll().stream()
        .map(Role::getName)
        .sorted(Comparator.naturalOrder())
        .toList();
  }
}
