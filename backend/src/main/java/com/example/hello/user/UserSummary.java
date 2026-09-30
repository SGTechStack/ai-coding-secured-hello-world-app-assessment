package com.example.hello.user;

import java.time.Instant;
import java.util.UUID;

/** Public projection of a user. The password hash is intentionally absent. */
public record UserSummary(
    UUID id, String username, String email, Role role, boolean enabled, Instant createdAt) {

  public static UserSummary from(User user) {
    return new UserSummary(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getRole(),
        user.isEnabled(),
        user.getCreatedAt());
  }
}
