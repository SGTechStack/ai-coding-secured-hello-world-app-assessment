package com.example.hello.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hello.support.IntegrationTestSupport;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Story 12, against the real context (the runner already executed at startup). */
class AdminBootstrapIntegrationTest extends IntegrationTestSupport {

  @Autowired private AdminBootstrap adminBootstrap;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("startup seeded exactly one ADMIN from configuration with a BCrypt hash")
  void seededAdminOnStartup() {
    User admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.isEnabled()).isTrue();
    assertThat(admin.getPasswordHash()).startsWith("$2");
    assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("running the bootstrap again does not create a duplicate")
  void rerunIsIdempotent() {
    long adminsBefore = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();

    adminBootstrap.run(new DefaultApplicationArguments());

    long adminsAfter = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
    assertThat(adminsAfter).isEqualTo(adminsBefore);
  }
}
