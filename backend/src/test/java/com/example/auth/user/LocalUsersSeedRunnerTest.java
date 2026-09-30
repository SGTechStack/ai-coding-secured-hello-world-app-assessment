package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for the local-user seeding decision logic, mirroring {@link
 * AdminBootstrapRunnerTest}'s mocked style rather than a {@code
 * @SpringBootTest} for the same shared-H2-instance reason.
 */
@ExtendWith(MockitoExtension.class)
class LocalUsersSeedRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsMissingUserWithHashedPasswordAndGivenRole() {
        LocalUsersProperties.UserEntry entry = new LocalUsersProperties.UserEntry("alice", Role.ADMIN, "DevAlicePass123!");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("DevAlicePass123!")).thenReturn("hashed-password");

        new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of(entry))).run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("alice");
        assertThat(savedUser.getValue().getEmail()).isEqualTo("alice@localhost");
        assertThat(savedUser.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void emailIsAlwaysDerivedFromUsername() {
        LocalUsersProperties.UserEntry entry = new LocalUsersProperties.UserEntry("bob", Role.USER, "DevBobPass123!");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("DevBobPass123!")).thenReturn("hashed-pw");

        new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of(entry))).run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("bob@localhost");
    }

    @Test
    void skipsEntryWhoseUsernameAlreadyExists() {
        LocalUsersProperties.UserEntry entry = new LocalUsersProperties.UserEntry("alice", Role.USER, "DevBobPass123!");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of(entry))).run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void seedsSomeEntriesAndSkipsOthersInSameRun() {
        LocalUsersProperties.UserEntry existing = new LocalUsersProperties.UserEntry("alice", Role.ADMIN, "pw1");
        LocalUsersProperties.UserEntry missing = new LocalUsersProperties.UserEntry("bob", Role.USER, "DevBobPass234!");
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("DevBobPass234!")).thenReturn("hashed-pw2");

        new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of(existing, missing)))
                .run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("bob");
    }

    @Test
    void emptyUsersListIsNoOp() {
        new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of())).run();

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void weakSeedPasswordFailsStartupRatherThanSeedingAWeakCredential() {
        LocalUsersProperties.UserEntry entry = new LocalUsersProperties.UserEntry("carol", Role.USER, "short11chr");
        when(userRepository.existsByUsername("carol")).thenReturn(false);

        LocalUsersSeedRunner runner =
                new LocalUsersSeedRunner(userRepository, passwordEncoder, new LocalUsersProperties(List.of(entry)));

        assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
