package org.eds.demo.user.domain;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Security principal for OIDC-authenticated users (ADR-DEMO-BE-0012).
 *
 * <p>Extends {@link AppUserDetails} so every existing {@code @AuthenticationPrincipal
 * AppUserDetails} injection and the {@link org.eds.demo.common.AuditUserListener} cast keep
 * working, and implements {@link OidcUser} so the ID token survives on the principal for
 * RP-initiated logout. OIDC claim/token access is delegated to the wrapped {@link OidcUser}
 * produced by the user-info service; authority and identity come from the app's own {@link AppUser}
 * record.
 */
public class AppOidcUser extends AppUserDetails implements OidcUser {

  private final transient OidcUser delegate;

  public AppOidcUser(
      UserId userId,
      String username,
      String displayName,
      Collection<? extends GrantedAuthority> authorities,
      OidcUser delegate) {
    // Password is irrelevant for OIDC — credentials are verified by AAS, never checked here.
    super(userId, username, displayName, "", authorities);
    this.delegate = delegate;
  }

  @Override
  public Map<String, Object> getClaims() {
    return delegate.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return delegate.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return delegate.getIdToken();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return delegate.getAttributes();
  }

  /** App username is the stable principal name (not the IdP subject). */
  @Override
  public String getName() {
    return getUsername();
  }
}
