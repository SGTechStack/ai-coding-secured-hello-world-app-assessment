package com.example.demo_app.user;

/**
 * What it takes to create an account. The username and email may be in any case (they are
 * normalised on the way in); the first name is stored exactly as given; the password is the
 * plaintext, which is hashed and never stored or logged.
 */
public record NewAccount(String username, String email, String firstName, String password) {

  /** Keeps the plaintext password out of any accidental log or error message. */
  @Override
  public String toString() {
    return "NewAccount[username=" + username + ", email=" + email + ", password=<redacted>]";
  }
}
