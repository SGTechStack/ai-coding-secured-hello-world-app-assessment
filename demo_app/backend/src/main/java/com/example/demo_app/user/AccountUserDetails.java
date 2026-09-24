package com.example.demo_app.user;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Security principal for a {@link UserAccount}. Carries the first name and role so session-bound
 * callers can build a profile without another database read, the role's {@code ROLE_*} authority
 * for authorization, and the account's {@code enabled} flag and lock state (checked only after the
 * password, see {@code SecurityConfig}). Stored in the {@code HttpSession}, so
 * it must stay {@link java.io.Serializable}.
 */
public class AccountUserDetails extends User {

  private final String firstName;
  private final Role role;

  /** The principal as of {@code now}, which decides whether the account is locked. */
  AccountUserDetails(UserAccount account, Instant now) {
    super(
        account.getUsername(),
        account.getPasswordHash(),
        account.isEnabled(),
        true,
        true,
        !account.isLockedAt(now),
        List.of(new SimpleGrantedAuthority(account.getRole().authority())));
    this.firstName = account.getFirstName();
    this.role = account.getRole();
  }

  public String getFirstName() {
    return firstName;
  }

  public Role getRole() {
    return role;
  }
}
