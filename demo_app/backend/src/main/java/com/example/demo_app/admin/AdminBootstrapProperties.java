package com.example.demo_app.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.admin}: the first admin account, created at startup only while no {@code ADMIN}
 * exists (see {@link AdminBootstrap}). Only the {@code dev} and {@code test} profiles give values;
 * anywhere else they come from {@code APP_ADMIN_USERNAME}, {@code APP_ADMIN_EMAIL} and {@code
 * APP_ADMIN_PASSWORD}. Any of them may be absent: that is only an error when an admin is needed.
 *
 * @param username the admin's username, normalised like any other
 * @param email the admin's email, normalised like any other
 * @param password the plaintext password, held to the password policy; never logged
 */
@ConfigurationProperties("app.admin")
record AdminBootstrapProperties(String username, String email, String password) {

  /** Keeps the plaintext password out of any accidental log or startup failure. */
  @Override
  public String toString() {
    return "AdminBootstrapProperties[username="
        + username
        + ", email="
        + email
        + ", password=<redacted>]";
  }
}
