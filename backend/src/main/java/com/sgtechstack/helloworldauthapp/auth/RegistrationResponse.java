package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a successful registration. Deliberately excludes the
 * password hash.
 */
public record RegistrationResponse(
        UUID id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {
    public static RegistrationResponse from(User user) {
        return new RegistrationResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
