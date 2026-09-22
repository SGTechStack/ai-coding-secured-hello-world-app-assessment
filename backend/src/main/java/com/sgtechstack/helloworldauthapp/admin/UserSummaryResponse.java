package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately excludes the password hash — this is what an admin is
 * allowed to see about another account, never the credential itself.
 */
public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
