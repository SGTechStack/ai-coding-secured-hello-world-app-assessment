package hello.desk.user;

import java.time.Instant;
import java.util.UUID;

public record PublicUser(
        UUID id,
        String username,
        String email,
        String role,
        boolean enabled,
        Instant createdAt) {

    public static PublicUser of(UserAccount user) {
        return new PublicUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
