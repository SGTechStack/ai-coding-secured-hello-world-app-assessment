package sg.securedhello.admin;

import java.time.Instant;
import java.util.UUID;

import sg.securedhello.user.UserAccount;

/**
 * One account as the admin surface shows it (PRD Story 8): the PRD's fields and the UUID that addresses it. A response
 * record, never the entity, so no hash, token or factor field can reach the wire (T-ADM-008; T-ADM-016).
 *
 * @param id        the account's UUID (ADR-050)
 * @param username  the username
 * @param email     the email address
 * @param role      {@code USER} or {@code ADMIN}
 * @param enabled   whether an administrator has left the account enabled
 * @param createdAt when the account was created
 */
public record AdminUserView(UUID id, String username, String email, String role, boolean enabled, Instant createdAt) {

    static AdminUserView of(UserAccount account) {
        return new AdminUserView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
                account.isEnabled(), account.getCreatedAt());
    }
}
