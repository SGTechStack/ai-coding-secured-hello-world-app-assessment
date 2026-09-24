package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adapts our {@link User} entity to Spring Security's {@link UserDetails}
 * contract. {@code isAccountNonLocked} is what makes lockout actually
 * reject login attempts: Spring Security's {@code DaoAuthenticationProvider}
 * checks it (via {@code AccountStatusUserDetailsChecker}) and throws a
 * {@code LockedException} before password matching even runs, so a locked
 * account is rejected even when the correct password is supplied.
 *
 * Authorities are {@code ROLE_<x>} plus whatever fine-grained authorities
 * {@code app.security.role-mappings} entitles that role to (e.g. {@code
 * HELLO_READ} for {@code USER}) — the caller ({@link AppUserDetailsService})
 * resolves that list from configuration and passes it in, so this class
 * stays a plain adapter with no configuration dependency of its own.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(User user, List<String> mappedAuthorities) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.getLockedUntil() == null || Instant.now().isAfter(user.getLockedUntil());

        List<GrantedAuthority> resolved = new ArrayList<>();
        resolved.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        mappedAuthorities.forEach(authority -> resolved.add(new SimpleGrantedAuthority(authority)));
        this.authorities = List.copyOf(resolved);
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
