package com.example.auth.security;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * The authenticated principal: Spring Security's {@link User} plus the account's {@code public_id}
 * UUID, so audit logging and the logging-context filter can name the actor ({@code user.id})
 * without a database lookup and without ever logging the username. Serializable because it is
 * stored inside the Spring Session JDBC session; {@code equals}/{@code hashCode} stay
 * username-based (inherited).
 */
public class AppUserDetails extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID publicId;

    public AppUserDetails(
            UUID publicId,
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.publicId = publicId;
    }

    public UUID getPublicId() {
        return publicId;
    }
}
