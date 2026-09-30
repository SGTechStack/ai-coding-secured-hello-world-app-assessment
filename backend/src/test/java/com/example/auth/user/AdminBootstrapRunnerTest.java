package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for the admin-bootstrap decision logic: whether an ADMIN account
 * gets created on startup. Mocked rather than a {@code @SpringBootTest}, since
 * the real H2 database stays alive for the whole test JVM and would make
 * "empty users table" an awkward precondition to set up against shared state.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "DevAdminPass123!";
    private static final String ADMIN_EMAIL = "admin@localhost";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void emptyUsersTableSeedsOneAdminRow() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn("hashed-password");

        new AdminBootstrapRunner(userRepository, passwordEncoder, ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_EMAIL)
                .run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo(ADMIN_USERNAME);
        assertThat(savedUser.getValue().getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(savedUser.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shortAdminPasswordFailsStartupRatherThanSeedingAWeakCredential() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(userRepository, passwordEncoder, ADMIN_USERNAME, "short11chr", ADMIN_EMAIL);

        assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void existingAdminRowIsNotDuplicated() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        new AdminBootstrapRunner(userRepository, passwordEncoder, ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_EMAIL)
                .run();

        verify(userRepository, never()).save(any());
    }
}
