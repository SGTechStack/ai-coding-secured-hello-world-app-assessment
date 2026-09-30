package org.eds.demo.config;

import java.util.List;
import java.util.Set;
import org.eds.demo.user.domain.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local dev test users loaded from {@code app.local.users} in {@code application-local.properties}.
 */
@ConfigurationProperties(prefix = "app.local")
public record LocalUsersProperties(List<UserEntry> users) {

  public record UserEntry(String username, String email, Set<Role> roles) {
    public UserEntry {
      email = (email != null && !email.isBlank()) ? email.trim() : null;
      roles = roles == null ? Set.of() : roles;
    }

    public static UserEntry of(String username, Set<Role> roles) {
      return new UserEntry(username, null, roles);
    }
  }
}
