package com.assessment.auth.password;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BCrypt at cost 12 (spec.md S5). One encoder, used by every write and every comparison. */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder(PasswordProperties properties) {
    return new BCryptPasswordEncoder(properties.bcryptStrength());
  }
}
