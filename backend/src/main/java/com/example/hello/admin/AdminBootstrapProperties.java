package com.example.hello.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Story 12: credentials for the first ADMIN, bound from {@code app.admin.*}. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(
    @DefaultValue("") String username, @DefaultValue("") String email, @DefaultValue("") String password) {

  public boolean isComplete() {
    return !username.isBlank() && !email.isBlank() && !password.isBlank();
  }

  @Override
  public String toString() {
    return "AdminBootstrapProperties{username='" + username + "', email='" + email + "', password='***'}";
  }
}
