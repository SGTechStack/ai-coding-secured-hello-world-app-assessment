package com.example.auth.user;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dev-only extra local user accounts to seed on startup, beyond the single
 * ADMIN account handled by {@link AdminBootstrapRunner}. Bound from indexed
 * {@code app.local.users[n].*} properties -- see application-dev.properties.
 * Never populated in application.properties or application-prod.properties,
 * which is what keeps this dev-only without a code-level profile check: an
 * absent {@code app.local.users} key binds {@code users} to {@code null},
 * normalized below to an empty list, so {@link LocalUsersSeedRunner} is a
 * no-op outside dev.
 */
@ConfigurationProperties(prefix = "app.local")
public record LocalUsersProperties(List<UserEntry> users) {

    public LocalUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    /**
     * One seeded account. No {@code email} field -- {@code User.email} is
     * NOT NULL/unique, but these accounts are dev-only logins, so {@link
     * LocalUsersSeedRunner} always derives {@code <username>@localhost}
     * rather than carrying a redundant, always-defaulted property.
     */
    public record UserEntry(String username, @DefaultValue("USER") Role role, String password) {
    }
}
