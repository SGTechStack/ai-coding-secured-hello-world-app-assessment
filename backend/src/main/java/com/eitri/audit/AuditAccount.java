package com.eitri.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * An existing account named in an audit line: its id, which stays unique even after the username is
 * reused, and its stored (lower-case) username, so an operator can read who acted without a lookup.
 * Only accounts that exist are named; an unverified username typed at login never reaches the log.
 */
public record AuditAccount(UUID id, String username) {

    public AuditAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
    }
}
