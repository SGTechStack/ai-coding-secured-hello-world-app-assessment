package com.assessment.auth.security;

import com.assessment.auth.common.AuthenticatedUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * The authentication produced by {@link JsonAuthenticationFilter}.
 *
 * <p>A custom subclass exists because the login body is JSON rather than form-encoded. That has one
 * consequence worth stating: AuthN:112-118's authentication-method resolver switches on the
 * authentication <em>class name</em>, so it silently yields {@code "unknown"} for this type and
 * would breach Std:240. The audit site therefore sets {@code authentication.method} to {@code
 * password} explicitly, and spec.md S13 makes that a named required test.
 */
public class PasswordAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;
  private String credentials;

  /** Unauthenticated form, built from the request body. */
  public PasswordAuthenticationToken(String username, String password) {
    // Cast required: Spring Security 7 added a builder-taking constructor, so a bare `null` is
    // ambiguous between it and the authorities-taking one.
    super((Collection<? extends GrantedAuthority>) null);
    this.principal = username;
    this.credentials = password;
    setAuthenticated(false);
  }

  /** Authenticated form, built by the provider once the credential has been verified. */
  public PasswordAuthenticationToken(
      AuthenticatedUser principal, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.credentials = null;
    super.setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return credentials;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  @Override
  public void eraseCredentials() {
    super.eraseCredentials();
    this.credentials = null;
  }
}
