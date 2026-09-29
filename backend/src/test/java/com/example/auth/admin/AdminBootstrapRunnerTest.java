package com.example.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.example.auth.config.AdminProperties;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;

/**
 * Fast unit tests for the admin bootstrap logic (Story 12 idempotency + Story 51
 * fail-fast). No Spring context — collaborators are mocked.
 */
class AdminBootstrapRunnerTest {

    private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);

    private AdminBootstrapRunner runner(AdminProperties props) {
        return new AdminBootstrapRunner(users, encoder, CLOCK, props, environment);
    }

    private AdminProperties props(String password) {
        return new AdminProperties("admin", "admin@example.com", password);
    }

    private void run(AdminBootstrapRunner runner) {
        runner.run(new DefaultApplicationArguments());
    }

    @Test
    void seedsAdmin_whenNoneExists_withHashedPasswordAndAdminRole() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{}); // dev/local
        when(encoder.encode("strong-admin-pass-1")).thenReturn("$2a$hashed");

        run(runner(props("strong-admin-pass-1")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(saved.getPasswordHash()).isNotEqualTo("strong-admin-pass-1");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED);
    }

    @Test
    void idempotent_doesNotSeedWhenAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        run(runner(props("strong-admin-pass-1")));

        verify(users, never()).save(any());
    }

    @Test
    void failsFast_inNonDevProfile_withPlaceholderPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        AdminBootstrapRunner runner = runner(props("change-me-please-12+"));
        assertThatThrownBy(() -> run(runner)).isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void failsFast_whenPasswordBlank() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        AdminBootstrapRunner runner = runner(props("   "));
        assertThatThrownBy(() -> run(runner)).isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void failsFast_whenPasswordTooShort() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        AdminBootstrapRunner runner = runner(props("short"));
        assertThatThrownBy(() -> run(runner)).isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void devProfile_allowsPlaceholderPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{}); // local default = dev
        when(encoder.encode(anyString())).thenReturn("$2a$hashed");

        run(runner(props("change-me-please-12+")));

        verify(users).save(any(User.class)); // seeded, no exception
    }

    @Test
    void nonDevProfile_allowsStrongNonPlaceholderPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(encoder.encode(anyString())).thenReturn("$2a$hashed");

        run(runner(props("a-strong-prod-password-99")));

        verify(users).save(any(User.class));
    }
}
