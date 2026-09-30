package com.assessment.securedhelloworld.security;

import com.assessment.securedhelloworld.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Wraps {@link User} for Spring Security. Account status (enabled/locked) is delegated here so
 * that {@code AbstractUserDetailsAuthenticationProvider} rejects a locked or disabled account
 * BEFORE ever checking the password (IM8 ac-1: server-side enforcement, never trusted from
 * client-supplied state) — this is what makes "locked account + correct password is still
 * rejected" (PRD Story 2) fall out of Spring Security's own pre-authentication checks rather than
 * bespoke logic that could drift from it.
 */
public class AppUserDetails implements UserDetails, Serializable {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
