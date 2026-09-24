package com.example.helloauth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Unit-level coverage of the {@link User} → {@link UserDetails} mapping:
 * {@code locked_until} → {@code isAccountNonLocked()}, {@code enabled} →
 * {@code isEnabled()}, and the not-found path. The HTTP-seam tests only reach
 * the not-found lambda when it throws, so the exception type itself is
 * asserted here.
 */
class AppUserDetailsServiceTests {

    private UserRepository users;
    private AppUserDetailsService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new AppUserDetailsService(users, Clock.systemUTC());
    }

    private User enabledUser() {
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$hash");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    @Test
    void unknownUserThrowsUsernameNotFoundException() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        // Must be UsernameNotFoundException — anything else (e.g. an NPE from
        // a broken supplier) would still surface as a generic 401 at the HTTP
        // seam but breaks the contract this service advertises.
        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void enabledUnlockedUserMapsToActiveUserDetails() {
        User user = enabledUser();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("$2a$hash");
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_USER");
    }

    @Test
    void futureLockedUntilLocksTheAccount() {
        User user = enabledUser();
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("alice").isAccountNonLocked())
            .isFalse();
    }

    @Test
    void expiredLockedUntilDoesNotLockTheAccount() {
        User user = enabledUser();
        user.setLockedUntil(Instant.now().minusSeconds(600));
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("alice").isAccountNonLocked())
            .isTrue();
    }

    @Test
    void disabledUserMapsToDisabledUserDetails() {
        User user = enabledUser();
        user.setEnabled(false);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("alice").isEnabled())
            .isFalse();
    }
}
