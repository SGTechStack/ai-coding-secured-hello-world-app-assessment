package com.example.hello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hello.common.AuditLogger;
import com.example.hello.common.PasswordPolicyException;
import com.example.hello.support.MutableClock;
import com.example.hello.user.PasswordPolicy;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuditLogger audit;

  private AdminBootstrap bootstrap(String username, String email, String password) {
    return new AdminBootstrap(
        userRepository,
        passwordEncoder,
        new PasswordPolicy(),
        new AdminBootstrapProperties(username, email, password),
        audit,
        MutableClock.at("2026-01-01T00:00:00Z"));
  }

  @Test
  void seedsAdminWhenNoneExists() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(passwordEncoder.encode("Seed-Secret-Pass-1")).thenReturn("$2a$admin");

    bootstrap("admin", "Admin@Example.com", "Seed-Secret-Pass-1").run(new DefaultApplicationArguments());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getUsername()).isEqualTo("admin");
    assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$admin");
    verify(audit).event("ADMIN_BOOTSTRAPPED", "username", "admin");
  }

  @Test
  void skipsWhenAdminExists() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    bootstrap("admin", "admin@example.com", "Admin-Secret-Pass-1").run(new DefaultApplicationArguments());

    verify(userRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void failsFastWithoutCredentials() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

    assertThatThrownBy(() -> bootstrap("admin", "admin@example.com", "").run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_ADMIN_PASSWORD");
    verify(userRepository, never()).save(any());
  }

  @Test
  void enforcesPasswordPolicyOnSeed() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

    assertThatThrownBy(() -> bootstrap("admin", "admin@example.com", "short").run(new DefaultApplicationArguments()))
        .isInstanceOf(PasswordPolicyException.class);
    assertThatThrownBy(
            () -> bootstrap("admin", "admin@example.com", "Admin-Secret-Pass-1").run(new DefaultApplicationArguments()))
        .as("password containing the username")
        .isInstanceOf(PasswordPolicyException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void propertiesToStringMasksPassword() {
    assertThat(new AdminBootstrapProperties("admin", "a@example.com", "Admin-Secret-Pass-1").toString())
        .doesNotContain("Admin-Secret-Pass-1");
  }
}
