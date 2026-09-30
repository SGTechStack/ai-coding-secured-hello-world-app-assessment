package com.example.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hello.common.AuditLogger;
import com.example.hello.config.AppSecurityProperties;
import com.example.hello.support.MutableClock;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

  private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private AuditLogger audit;

  private MutableClock clock;
  private AccountLockoutService service;
  private User alice;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(START);
    AppSecurityProperties props =
        new AppSecurityProperties(
            true,
            "Lax",
            Duration.ofMinutes(30),
            List.of(),
            new AppSecurityProperties.Login(3, Duration.ofMinutes(15), 20, Duration.ofMinutes(15)));
    service = new AccountLockoutService(userRepository, props, audit, clock);
    alice = new User("alice", "alice@example.com", "$2a$hash", Role.USER, START);
  }

  private void aliceExists() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
  }

  @Test
  void locksOnThirdConsecutiveFailure() {
    aliceExists();
    service.onFailure("alice");
    service.onFailure("alice");
    assertThat(alice.getLockedUntil()).isNull();
    assertThat(alice.getFailedLoginAttempts()).isEqualTo(2);

    service.onFailure("alice");

    assertThat(alice.getFailedLoginAttempts()).isEqualTo(3);
    assertThat(alice.getLockedUntil()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    assertThat(alice.isLockedAt(clock.instant())).isTrue();
    verify(audit).event(eq("ACCOUNT_LOCKED"), any(String[].class));
  }

  @Test
  void successResetsCounterAndLock() {
    aliceExists();
    service.onFailure("alice");
    service.onFailure("alice");
    service.onFailure("alice");

    service.onSuccess("alice");

    assertThat(alice.getFailedLoginAttempts()).isZero();
    assertThat(alice.getLockedUntil()).isNull();
  }

  @Test
  void lockExpiresAfterCooldownAndFailuresStartFresh() {
    aliceExists();
    service.onFailure("alice");
    service.onFailure("alice");
    service.onFailure("alice");
    clock.advance(Duration.ofMinutes(16));
    assertThat(alice.isLockedAt(clock.instant())).isFalse();

    service.onFailure("alice");

    assertThat(alice.getFailedLoginAttempts()).as("expired lock starts a new window").isEqualTo(1);
    assertThat(alice.isLockedAt(clock.instant())).isFalse();
  }

  @Test
  void unknownUsernameIsIgnored() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    service.onFailure("ghost");

    verify(audit, never()).event(any(), any(String[].class));
  }
}
