package com.example.hello.config;

import com.example.hello.auth.AuthRequests;
import com.example.hello.auth.PasswordPolicy;
import com.example.hello.user.*;
import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final PasswordPolicy policy;
  private final Validator validator;
  private final Clock clock;
  private final String username;
  private final String email;
  private final String password;

  public AdminBootstrap(
      UserRepository users,
      PasswordEncoder passwords,
      PasswordPolicy policy,
      Validator validator,
      Clock clock,
      @Value("${app.admin.username}") String username,
      @Value("${app.admin.email}") String email,
      @Value("${app.admin.password}") String password) {
    this.users = users;
    this.passwords = passwords;
    this.policy = policy;
    this.validator = validator;
    this.clock = clock;
    this.username = username;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (users.existsByRole(Role.ADMIN)) return;
    AuthRequests.Register credentials = new AuthRequests.Register(username, email, password);
    if (!validator.validate(credentials).isEmpty()) {
      throw new IllegalStateException(
          "Set valid APP_ADMIN_USERNAME, APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD to bootstrap the first administrator.");
    }
    policy.validate(password);
    if (users.existsByUsername(credentials.username())
        || users.existsByEmail(credentials.email())) {
      throw new IllegalStateException(
          "Bootstrap administrator username/email conflicts with an existing account.");
    }
    users.save(
        new UserAccount(
            credentials.username(),
            credentials.email(),
            passwords.encode(password),
            Role.ADMIN,
            clock.instant()));
  }
}
