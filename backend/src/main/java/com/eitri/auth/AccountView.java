package com.eitri.auth;

import java.time.Instant;
import java.util.UUID;

/** What other features may see of an account: never the hash, the failure counter or the lock time. */
public record AccountView(UUID id, String username, String email, Role role, boolean enabled, Instant createdAt) {

    static AccountView of(Account account) {
        return new AccountView(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getRole(),
                account.isEnabled(),
                account.getCreatedAt());
    }
}
