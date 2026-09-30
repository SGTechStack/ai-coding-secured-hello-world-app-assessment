package com.example.hello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hello.auth.SessionInvalidationService;
import com.example.hello.common.AuditLogger;
import com.example.hello.common.NotFoundException;
import com.example.hello.common.SelfActionException;
import com.example.hello.passwordreset.PasswordResetTokenRepository;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import com.example.hello.user.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository tokenRepository;
  @Mock private SessionInvalidationService sessionInvalidation;
  @Mock private AuditLogger audit;

  private AdminUserService service;
  private User admin;
  private User bob;

  @BeforeEach
  void setUp() {
    service = new AdminUserService(userRepository, tokenRepository, sessionInvalidation, audit);
    admin = new User("admin", "admin@example.com", "$2a$a", Role.ADMIN, Instant.EPOCH);
    bob = new User("bob", "bob@example.com", "$2a$b", Role.USER, Instant.EPOCH);
  }

  @Test
  void listsSummariesWithoutHashes() {
    when(userRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(admin, bob));

    List<UserSummary> users = service.listUsers();

    assertThat(users).extracting(UserSummary::username).containsExactly("admin", "bob");
    assertThat(users.get(1).toString()).doesNotContain("$2a$");
  }

  @Test
  void disablesAnotherUserAndRevokesTheirSessions() {
    when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

    UserSummary result = service.setEnabled("admin", bob.getId(), false);

    assertThat(result.enabled()).isFalse();
    assertThat(bob.isEnabled()).isFalse();
    verify(sessionInvalidation).invalidateAllForUser("bob");
    verify(audit).event("USER_DISABLED", "actor", "admin", "target", "bob");
  }

  @Test
  void enablingDoesNotRevokeSessions() {
    bob.setEnabled(false);
    when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

    service.setEnabled("admin", bob.getId(), true);

    assertThat(bob.isEnabled()).isTrue();
    verify(sessionInvalidation, never()).invalidateAllForUser(any());
  }

  @Test
  void adminCannotDisableThemselves() {
    when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.setEnabled("admin", admin.getId(), false))
        .isInstanceOf(SelfActionException.class);
    assertThat(admin.isEnabled()).isTrue();
  }

  @Test
  void changesRoleOfAnotherUser() {
    when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

    UserSummary result = service.changeRole("admin", bob.getId(), Role.ADMIN);

    assertThat(result.role()).isEqualTo(Role.ADMIN);
    verify(sessionInvalidation).invalidateAllForUser("bob");
  }

  @Test
  void adminCannotDemoteThemselves() {
    when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.changeRole("admin", admin.getId(), Role.USER))
        .isInstanceOf(SelfActionException.class);
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void deletesAnotherUserWithTokensAndSessions() {
    when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

    service.deleteUser("admin", bob.getId());

    verify(tokenRepository).deleteByUser(bob);
    verify(sessionInvalidation).invalidateAllForUser("bob");
    verify(userRepository).delete(bob);
    verify(audit).event("USER_DELETED", "actor", "admin", "target", "bob");
  }

  @Test
  void adminCannotDeleteThemselves() {
    when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.deleteUser("admin", admin.getId()))
        .isInstanceOf(SelfActionException.class);
    verify(userRepository, never()).delete(any());
  }

  @Test
  void unknownTargetIsNotFound() {
    UUID missing = UUID.randomUUID();
    when(userRepository.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteUser("admin", missing)).isInstanceOf(NotFoundException.class);
  }
}
