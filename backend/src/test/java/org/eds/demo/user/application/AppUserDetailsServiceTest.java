package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eds.demo.config.LocalUsersProperties;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private Environment environment;

  // Tests supply this via constructor — see helper methods below.
  // @InjectMocks cannot satisfy the Optional<LocalUsersProperties> field automatically,
  // so we build the service manually in each test.

  // ── helpers ──────────────────────────────────────────────────────────────

  private AppUserDetailsService serviceWithLocalUsers(LocalUsersProperties props) {
    return new AppUserDetailsService(appUserRepository, environment, Optional.of(props));
  }

  private AppUserDetailsService serviceWithoutLocalUsers() {
    return new AppUserDetailsService(appUserRepository, environment, Optional.empty());
  }

  // ── tests ─────────────────────────────────────────────────────────────────

  @Test
  void loadsExistingUser_localProfile() {
    var props =
        new LocalUsersProperties(
            List.of(
                LocalUsersProperties.UserEntry.of("alice", Set.of(Role.USER, Role.USER_MANAGER))));
    var service = serviceWithLocalUsers(props);

    var user = AppUser.create("alice", Set.of(Role.USER, Role.USER_MANAGER));
    when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(environment.matchesProfiles("local")).thenReturn(true);

    var details = service.loadUserByUsername("alice");

    assertThat(details.getUsername()).isEqualTo("alice");
    assertThat(details.getPassword()).isEqualTo("{noop}password");
    assertThat(details.getAuthorities())
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_USER_MANAGER");
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void provisionsNewUser_whenNotFoundInRepository() {
    var props =
        new LocalUsersProperties(
            List.of(LocalUsersProperties.UserEntry.of("bob", Set.of(Role.USER))));
    var service = serviceWithLocalUsers(props);

    var newUser = AppUser.create("bob", Set.of(Role.USER));
    when(appUserRepository.findByUsername("bob")).thenReturn(Optional.empty());
    when(appUserRepository.save(any(AppUser.class))).thenReturn(newUser);
    when(environment.matchesProfiles("local")).thenReturn(true);

    var details = service.loadUserByUsername("bob");

    assertThat(details.getUsername()).isEqualTo("bob");
    verify(appUserRepository).save(any(AppUser.class));
  }

  @Test
  void nonLocalProfile_usesEmptyPassword() {
    var service = serviceWithoutLocalUsers();

    var user = AppUser.create("carol", Set.of(Role.USER));
    when(appUserRepository.findByUsername("carol")).thenReturn(Optional.of(user));
    when(environment.matchesProfiles("local")).thenReturn(false);

    var details = service.loadUserByUsername("carol");

    assertThat(details.getPassword()).isEmpty();
  }

  @Test
  void resolveRoles_fallsBackToUserRole_whenUsernameNotInLocalList() {
    var props =
        new LocalUsersProperties(
            List.of(LocalUsersProperties.UserEntry.of("alice", Set.of(Role.USER_MANAGER))));
    var service = serviceWithLocalUsers(props);

    // "unknown" is not in the local users list → should fall back to Role.USER
    var savedUser = AppUser.create("unknown", Set.of(Role.USER));
    when(appUserRepository.findByUsername("unknown")).thenReturn(Optional.empty());
    when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);
    when(environment.matchesProfiles("local")).thenReturn(true);

    var details = service.loadUserByUsername("unknown");

    assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
  }

  @Test
  void resolveRoles_fallsBackToUserRole_whenNoLocalUsersProperties() {
    var service = serviceWithoutLocalUsers();

    var savedUser = AppUser.create("dave", Set.of(Role.USER));
    when(appUserRepository.findByUsername("dave")).thenReturn(Optional.empty());
    when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);
    when(environment.matchesProfiles("local")).thenReturn(false);

    var details = service.loadUserByUsername("dave");

    assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
  }

  @Test
  void provisionsNewUser_withConfiguredEmail() {
    var props =
        new LocalUsersProperties(
            List.of(
                new LocalUsersProperties.UserEntry("dave", "dave@example.com", Set.of(Role.USER))));
    var service = serviceWithLocalUsers(props);

    when(appUserRepository.findByUsername("dave")).thenReturn(Optional.empty());
    when(appUserRepository.save(any(AppUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(environment.matchesProfiles("local")).thenReturn(true);

    var details = service.loadUserByUsername("dave");

    assertThat(details.getUsername()).isEqualTo("dave");
    org.mockito.ArgumentCaptor<AppUser> captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
    verify(appUserRepository).save(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo("dave@example.com");
  }

  @Test
  void updatesExistingUserEmail_whenConfiguredEmailDiffers() {
    var props =
        new LocalUsersProperties(
            List.of(
                new LocalUsersProperties.UserEntry(
                    "dave", "dave-new@example.com", Set.of(Role.USER))));
    var service = serviceWithLocalUsers(props);

    var existingUser = AppUser.create("dave", Set.of(Role.USER));
    existingUser.updateEmail("dave-old@example.com");

    when(appUserRepository.findByUsername("dave")).thenReturn(Optional.of(existingUser));
    when(appUserRepository.save(any(AppUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(environment.matchesProfiles("local")).thenReturn(true);

    service.loadUserByUsername("dave");

    verify(appUserRepository).save(existingUser);
    assertThat(existingUser.getEmail()).isEqualTo("dave-new@example.com");
  }

  @Test
  void clearsExistingUserEmail_whenConfiguredEmailIsEmpty() {
    var props =
        new LocalUsersProperties(
            List.of(new LocalUsersProperties.UserEntry("dave", "", Set.of(Role.USER))));
    var service = serviceWithLocalUsers(props);

    var existingUser = AppUser.create("dave", Set.of(Role.USER));
    existingUser.updateEmail("dave@example.com");

    when(appUserRepository.findByUsername("dave")).thenReturn(Optional.of(existingUser));
    when(appUserRepository.save(any(AppUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(environment.matchesProfiles("local")).thenReturn(true);

    service.loadUserByUsername("dave");

    verify(appUserRepository).save(existingUser);
    assertThat(existingUser.getEmail()).isNull();
  }
}
