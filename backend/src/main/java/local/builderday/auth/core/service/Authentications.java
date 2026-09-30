package local.builderday.auth.core.service;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;

/** Questions about an {@link Authentication} that more than one auth use case asks. */
public final class Authentications {
  private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

  private Authentications() {}

  /** @return whether {@code authentication} is a logged-in User, not absent or anonymous */
  public static boolean isAuthenticatedUser(Authentication authentication) {
    return TRUST.isAuthenticated(authentication);
  }
}
