package local.builderday.account.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {
  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordHistory passwordHistory = mock(PasswordHistory.class);
  private final UserProfileService service = new UserProfileService(userRepository, null, passwordHistory, "USER");

  @Test
  void should_returnTheAccountId_when_theUsernameIsKnown() {
    var id = UUID.randomUUID();
    when(userRepository.findByUsername("testuser123"))
        .thenReturn(Optional.of(
            new UserEntity(id, "testuser123", "testuser123@test.example.com", "hash", "USER", true)));

    assertThat(service.findIdForAudit("testuser123")).isEqualTo(id);
  }

  @Test
  void should_returnNull_when_theUsernameIsUnknownOrTheLookupFails() {
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
    when(userRepository.findByUsername("broken")).thenThrow(new IllegalStateException("database unavailable"));

    assertThat(service.findIdForAudit("unknown")).isNull();
    assertThat(service.findIdForAudit("broken")).isNull();
  }

  @Test
  void should_checkUsernameOnly_when_creatingAnEmaillessAccount() {
    // An emailless account (Admin bootstrap) must not collide with another emailless account on a null email, so the
    // duplicate check is username-only and never the username-or-email form (which matches null against null).
    when(userRepository.existsByUsernameIgnoreCase("admin.one")).thenReturn(false);

    assertThat(service.createAccount("admin.one", null, "hash", "ADMIN")).isPresent();
    verify(userRepository).existsByUsernameIgnoreCase("admin.one");
    verify(userRepository, never()).existsByUsernameIgnoreCaseOrEmailIgnoreCase(anyString(), any());
  }

  @Test
  void should_checkUsernameAndEmail_when_creatingAnAccountWithAnEmail() {
    when(userRepository.existsByUsernameIgnoreCaseOrEmailIgnoreCase("jane", "jane@test.example.com"))
        .thenReturn(false);

    assertThat(service.createAccount("jane", "jane@test.example.com", "hash", "USER")).isPresent();
    verify(userRepository).existsByUsernameIgnoreCaseOrEmailIgnoreCase("jane", "jane@test.example.com");
    verify(userRepository, never()).existsByUsernameIgnoreCase(anyString());
  }
}
