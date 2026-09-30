package org.eds.demo.auth.api;

/** Credentials posted by the sign-in form. */
public record SignInRequest(String username, String password) {

  /** Keeps the plaintext password out of accidental logging. */
  @Override
  public String toString() {
    return "SignInRequest[username=" + username + "]";
  }
}
