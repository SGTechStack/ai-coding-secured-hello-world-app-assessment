package com.example.demo_app.user;

import java.util.List;
import org.springframework.security.core.userdetails.User;

/**
 * Security principal for a {@link UserAccount}. Carries the first name so session-bound callers
 * can build a profile without another database read. Stored in the {@code HttpSession}, so it
 * must stay {@link java.io.Serializable}.
 */
public class AccountUserDetails extends User {

  private final String firstName;

  AccountUserDetails(UserAccount account) {
    super(account.getUsername(), account.getPasswordHash(), List.of());
    this.firstName = account.getFirstName();
  }

  public String getFirstName() {
    return firstName;
  }

}
