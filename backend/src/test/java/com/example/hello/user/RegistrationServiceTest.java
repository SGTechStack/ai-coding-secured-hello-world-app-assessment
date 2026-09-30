package com.example.hello.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hello.common.AuditLogger;
import com.example.hello.common.ConflictException;
import com.example.hello.common.PasswordPolicyException;
import com.example.hello.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuditLogger audit;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new RegistrationService(
            userRepository, passwordEncoder, new PasswordPolicy(), audit, MutableClock.at("2026-01-01T00:00:00Z"));
  }

  @Test
  void createsEnabledUserWithEncodedPasswordAndLowercasedEmail() {
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
    when(passwordEncoder.encode("Correct-Horse-Battery")).thenReturn("$2a$hash");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UserSummary summary =
        service.register(new RegisterRequest(" alice ", "Alice@Example.com", "Correct-Horse-Battery"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    User user = saved.getValue();
    assertThat(user.getUsername()).isEqualTo("alice");
    assertThat(user.getEmail()).isEqualTo("alice@example.com");
    assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
    assertThat(user.getRole()).isEqualTo(Role.USER);
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.getFailedLoginAttempts()).isZero();
    assertThat(summary.id()).isEqualTo(user.getId());
    assertThat(summary.toString()).doesNotContain("$2a$hash");
    verify(audit).event("USER_REGISTERED", "username", "alice");
  }

  @Test
  void rejectsTakenUsernameWithoutSaving() {
    when(userRepository.existsByUsername("alice")).thenReturn(true);

    assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "a@example.com", "Correct-Horse-Battery")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Username is already taken");
    verify(userRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void rejectsRegisteredEmailWithoutSaving() {
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "a@example.com", "Correct-Horse-Battery")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Email is already registered");
    verify(userRepository, never()).save(any());
  }

  @Test
  void rejectsWeakPasswordWithoutSaving() {
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("a@example.com")).thenReturn(false);

    assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "a@example.com", "tooshort")))
        .isInstanceOf(PasswordPolicyException.class);
    verify(userRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void requestToStringNeverRevealsPassword() {
    assertThat(new RegisterRequest("alice", "a@example.com", "Correct-Horse-Battery").toString())
        .doesNotContain("Correct-Horse-Battery");
  }
}
