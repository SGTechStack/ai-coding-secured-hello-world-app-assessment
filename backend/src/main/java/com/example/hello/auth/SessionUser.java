package com.example.hello.auth;

import com.example.hello.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** What the frontend learns about the current session. */
public record SessionUser(boolean authenticated, String username, Role role) {

  public static SessionUser anonymous() {
    return new SessionUser(false, null, null);
  }

  public static SessionUser from(Authentication authentication) {
    boolean admin =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(Role::isAdminAuthority);
    return new SessionUser(true, authentication.getName(), admin ? Role.ADMIN : Role.USER);
  }
}
