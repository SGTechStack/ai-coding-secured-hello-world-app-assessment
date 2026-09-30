package org.eds.demo.user.application;

import java.time.Instant;
import org.eds.demo.user.domain.Role;

/**
 * Outcome of creating an Account. The plaintext Temporary Password exists only here, for the one
 * response; never log it.
 */
public record CreatedAccount(
    String username, Role role, String temporaryPassword, Instant temporaryPasswordExpiresAt) {

  @Override
  public String toString() {
    return "CreatedAccount[username=" + username + ", role=" + role + "]";
  }
}
