package sg.securedhello.admin;

import java.time.Instant;
import java.util.UUID;

import sg.securedhello.user.UserAccount;

/**
 * One account as the admin surface shows it (PRD Story 8): the PRD's fields, the UUID that addresses it, and its
 * sign-in status, apart from being enabled (ADR-075). A response record, never the entity, so no hash, token, secret,
 * device or factor field can reach the wire (T-ADM-008; T-ADM-016).
 *
 * @param id           the account's UUID (ADR-050)
 * @param username     the username
 * @param email        the email address
 * @param role         {@code USER} or {@code ADMIN}
 * @param enabled      whether an administrator has left the account enabled
 * @param activated    whether the account has set its first password; a pending registration or invite has not
 * @param createdAt    when the account was created
 * @param signInStatus its locks and disables, as they stand now
 */
public record AdminUserView(UUID id, String username, String email, String role, boolean enabled, boolean activated,
        Instant createdAt, SignInStatus signInStatus) {

    static AdminUserView of(UserAccount account, SignInStatus signInStatus) {
        return new AdminUserView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
                account.isEnabled(), !account.isPending(), account.getCreatedAt(), signInStatus);
    }
}
