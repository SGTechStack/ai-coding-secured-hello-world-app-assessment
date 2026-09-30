package com.example.hello.user;

/** Account roles. Authorities are derived server-side; never trusted from the client. */
public enum Role {
  USER,
  ADMIN;

  private static final String PREFIX = "ROLE_";

  public String authority() {
    return PREFIX + name();
  }

  public static boolean isAdminAuthority(String authority) {
    return ADMIN.authority().equals(authority);
  }
}
