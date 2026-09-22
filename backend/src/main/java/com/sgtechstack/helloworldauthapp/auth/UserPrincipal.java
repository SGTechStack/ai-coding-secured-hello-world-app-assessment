package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adapts our {@link User} entity to Spring Security's {@link UserDetails}
 * contract. {@code isAccountNonLocked} is what makes lockout actually
 * reject login attempts: Spring Security's {@code DaoAuthenticationProvider}
 * checks it (via {@code AccountStatusUserDetailsChecker}) and throws a
 * {@code LockedException} before password matching even runs, so a locked
 * account is rejected even when the correct password is supplied.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.getLockedUntil() == null || Instant.now().isAfter(user.getLockedUntil());
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public UUID getId() {
        return id;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
