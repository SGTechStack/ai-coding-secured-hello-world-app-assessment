package local.builderday.account.core.model;

import java.io.Serial;
import java.util.UUID;
import local.builderday.common.web.AuthenticatedUser;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The Account a User or Admin is authenticated as: Spring Security's standard user plus the Account's id, so requests
 * are attributed without a lookup. It carries exactly the authorities and account state of the {@link UserDetails} it
 * wraps. The one {@code model/} type allowed to depend on Spring Security (BackendArchitectureTest).
 */
// Spring Security's User is fully qualified: this package's User is the domain model (ADR 0011).
public final class AccountPrincipal extends org.springframework.security.core.userdetails.User
    implements AuthenticatedUser {
  @Serial private static final long serialVersionUID = 1L;
  private final UUID userId;

  public AccountPrincipal(UUID userId, UserDetails details) {
    super(details.getUsername(), details.getPassword(), details.isEnabled(), details.isAccountNonExpired(),
        details.isCredentialsNonExpired(), details.isAccountNonLocked(), details.getAuthorities());
    this.userId = userId;
  }

  @Override
  public UUID userId() { return userId; }
}
