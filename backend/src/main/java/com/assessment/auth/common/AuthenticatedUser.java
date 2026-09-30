package com.assessment.auth.common;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>Carries the {@code id} because {@code user.id} is the only identity ever written to a log line
 * — {@code user.name} is never logged, and reading the audit trail requires database access to
 * resolve UUIDs (spec.md S11).
 *
 * <p>Lock state is <em>not</em> carried here. It is derived solely from {@code locked_until} at
 * authentication time (spec.md S2, S6); a second source of truth is a silent auth bypass.
 */
public record AuthenticatedUser(
    UUID id, String username, String passwordHash, String role, boolean requirePasswordChange)
    implements UserDetails {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }
}
