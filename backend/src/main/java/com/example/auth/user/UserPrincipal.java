package com.example.auth.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Serializable Spring Security principal built from a User entity snapshot.
 * Stores no JPA entity reference so it can be safely serialized into and out
 * of Spring Session JDBC without proxy/lazy-loading issues.
 *
 * isAccountNonLocked() is evaluated once at construction (via User.isLocked(now))
 * and frozen into the field — consistent with the session lifetime. New logins
 * re-evaluate via a fresh UserDetailsService load.
 */
public class UserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;

    private UserPrincipal(UUID userId, String username, String passwordHash,
                          Role role, boolean enabled, boolean locked) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.locked = locked;
    }

    public static UserPrincipal of(User user, Instant now) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled(),
                user.isLocked(now));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
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
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }
}
