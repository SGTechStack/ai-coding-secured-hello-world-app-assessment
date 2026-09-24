package com.example.demo_app.admin;

import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;
import java.time.Instant;

/**
 * One row of the admin user list. It is an allow-list of what an admin may see: the password hash,
 * the lockout fields and reset tokens are never copied in, so they can't leak through the API
 * whatever the entity gains later.
 */
record AdminUserView(
    Long id,
    String username,
    String email,
    String firstName,
    Role role,
    boolean enabled,
    Instant createdAt) {

  static AdminUserView of(UserAccount account) {
    return new AdminUserView(
        account.getId(),
        account.getUsername(),
        account.getEmail(),
        account.getFirstName(),
        account.getRole(),
        account.isEnabled(),
        account.getCreatedAt());
  }
}
