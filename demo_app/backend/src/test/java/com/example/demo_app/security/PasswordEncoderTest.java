package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** New hashes use BCrypt cost 12; hashes made at the old cost 10 keep verifying. */
@SpringBootTest
@ActiveProfiles("test")
class PasswordEncoderTest {

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void newHashesUseCost12() {
    assertThat(passwordEncoder.encode("correct horse battery")).startsWith("$2a$12$");
  }

  @Test
  void cost10SeedHashStillVerifies() {
    String seedHash =
        jdbc.queryForObject(
            "SELECT password_hash FROM user_account WHERE username = 'johndoe'", String.class);

    assertThat(seedHash).startsWith("$2a$10$");
    assertThat(passwordEncoder.matches("Password123!", seedHash)).isTrue();
  }
}
