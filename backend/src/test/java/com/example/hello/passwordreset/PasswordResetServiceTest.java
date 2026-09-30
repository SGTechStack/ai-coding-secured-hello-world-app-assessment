package com.example.hello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hello.auth.SessionInvalidationService;
import com.example.hello.common.AuditLogger;
import com.example.hello.common.InvalidResetTokenException;
import com.example.hello.common.PasswordPolicyException;
import com.example.hello.config.PasswordResetProperties;
import com.example.hello.support.MutableClock;
import com.example.hello.user.PasswordPolicy;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository tokenRepository;
  @Mock private EmailService emailService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionInvalidationService sessionInvalidation;
  @Mock private AuditLogger audit;

  private MutableClock clock;
  private PasswordResetService service;
  private User alice;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(START);
    service =
        new PasswordResetService(
            userRepository,
            tokenRepository,
            emailService,
            passwordEncoder,
            new PasswordPolicy(),
            sessionInvalidation,
            new PasswordResetProperties(Duration.ofMinutes(20), "http://localhost:3000/reset-password"),
            audit,
            clock);
    alice = new User("alice", "alice@example.com", "$2a$old", Role.USER, START);
  }

  @Test
  void unknownEmailIsASilentNoOp() {
    when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

    service.requestReset("nobody@example.com");

    verify(tokenRepository, never()).save(any());
    verify(emailService, never()).sendPasswordResetEmail(any(), any());
  }

  @Test
  void knownEmailStoresHashNotTokenAndEmailsTheLink() {
    when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(alice));

    service.requestReset(" alice@example.com ");

    ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(tokenRepository).deleteByUser(alice);
    verify(tokenRepository).save(stored.capture());
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), link.capture());

    String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);
    assertThat(link.getValue()).startsWith("http://localhost:3000/reset-password?token=");
    assertThat(stored.getValue().getTokenHash()).isEqualTo(TokenHasher.hash(token)).isNotEqualTo(token);
    assertThat(stored.getValue().getExpiresAt()).isEqualTo(START.plus(Duration.ofMinutes(20)));
    assertThat(stored.getValue().isUsed()).isFalse();
    verify(audit).event("PASSWORD_RESET_REQUESTED", "username", "alice");
  }

  @Test
  void validTokenUpdatesPasswordMarksUsedAndInvalidatesSessions() {
    String token = TokenHasher.generateToken();
    PasswordResetToken resetToken = new PasswordResetToken(alice, TokenHasher.hash(token), START.plus(Duration.ofMinutes(20)));
    when(tokenRepository.findByTokenHash(TokenHasher.hash(token))).thenReturn(Optional.of(resetToken));
    when(passwordEncoder.encode("Brand-New-Secret-42")).thenReturn("$2a$new");
    when(sessionInvalidation.invalidateAllForUser("alice")).thenReturn(2);

    service.confirmReset(token, "Brand-New-Secret-42");

    assertThat(alice.getPasswordHash()).isEqualTo("$2a$new");
    assertThat(resetToken.getUsedAt()).isEqualTo(START);
    verify(sessionInvalidation).invalidateAllForUser("alice");
    verify(audit).event(eq("PASSWORD_RESET_COMPLETED"), eq("username"), eq("alice"), eq("sessions_invalidated"), eq("2"));
  }

  @Test
  void expiredTokenIsRejectedWithoutChange() {
    String token = TokenHasher.generateToken();
    PasswordResetToken resetToken = new PasswordResetToken(alice, TokenHasher.hash(token), START.plus(Duration.ofMinutes(20)));
    when(tokenRepository.findByTokenHash(TokenHasher.hash(token))).thenReturn(Optional.of(resetToken));
    clock.advance(Duration.ofMinutes(21));

    assertThatThrownBy(() -> service.confirmReset(token, "Brand-New-Secret-42"))
        .isInstanceOf(InvalidResetTokenException.class);

    assertThat(alice.getPasswordHash()).isEqualTo("$2a$old");
    assertThat(resetToken.isUsed()).isFalse();
    verify(sessionInvalidation, never()).invalidateAllForUser(any());
  }

  @Test
  void usedTokenIsRejected() {
    String token = TokenHasher.generateToken();
    PasswordResetToken resetToken = new PasswordResetToken(alice, TokenHasher.hash(token), START.plus(Duration.ofMinutes(20)));
    resetToken.markUsed(START);
    when(tokenRepository.findByTokenHash(TokenHasher.hash(token))).thenReturn(Optional.of(resetToken));

    assertThatThrownBy(() -> service.confirmReset(token, "Brand-New-Secret-42"))
        .isInstanceOf(InvalidResetTokenException.class);
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void unknownTokenIsRejected() {
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmReset("garbage", "Brand-New-Secret-42"))
        .isInstanceOf(InvalidResetTokenException.class);
  }

  @Test
  void weakNewPasswordIsRejected() {
    String token = TokenHasher.generateToken();
    PasswordResetToken resetToken = new PasswordResetToken(alice, TokenHasher.hash(token), START.plus(Duration.ofMinutes(20)));
    when(tokenRepository.findByTokenHash(TokenHasher.hash(token))).thenReturn(Optional.of(resetToken));

    assertThatThrownBy(() -> service.confirmReset(token, "weak"))
        .isInstanceOf(PasswordPolicyException.class);
    assertThat(resetToken.isUsed()).isFalse();
    assertThat(alice.getPasswordHash()).isEqualTo("$2a$old");
  }
}
