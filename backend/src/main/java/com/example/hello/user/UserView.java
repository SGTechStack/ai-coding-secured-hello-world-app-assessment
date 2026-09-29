package com.example.hello.user;

import java.time.Instant;
import java.util.UUID;

public record UserView(
    UUID id, String username, String email, Role role, boolean enabled, Instant createdAt) {
  public static UserView from(UserAccount user) {
    return new UserView(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getRole(),
        user.isEnabled(),
        user.getCreatedAt());
  }
}
