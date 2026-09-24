package com.example.demo_app.user;

/**
 * What an account may do. Stored by name in {@code user_account.role}; the database restricts the
 * column to these values.
 */
public enum Role {
  USER,
  ADMIN;

  /** The Spring Security authority for this role, e.g. {@code ROLE_ADMIN}. */
  public String authority() {
    return "ROLE_" + name();
  }
}
