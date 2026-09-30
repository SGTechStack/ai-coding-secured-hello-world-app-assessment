package local.builderday.account.administration.controller;

import java.util.Arrays;
import java.util.List;
import local.builderday.account.core.model.Role;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/admin/roles}: the {@link Role} constants in declaration order, the values the role-change body takes
 * and a User-list row carries. Admins only (the {@code /api/admin/**} grant). Not audited: it is configuration and
 * holds no personal data.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class RolesController {
  private static final List<String> ROLES = Arrays.stream(Role.values()).map(Role::name).toList();

  @GetMapping("/api/admin/roles")
  public List<String> roles() {
    return ROLES;
  }
}
