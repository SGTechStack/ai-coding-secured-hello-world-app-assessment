package sg.securedhello.registration;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * When a pending registration has lapsed, and the deletion that frees its username and address (ADR-032 amendment of
 * 2026-09-29; ADR-007 amendment of 2026-09-30). Both a self-registration and an administrator's invite read the one
 * rule, so an identifier a lapsed pending registration held is free to either.
 *
 * <ul>
 *   <li>A <em>self-registered</em> pending registration lapses {@link #PENDING_PERIOD} after its last registration:
 *       its activation token's life, which each registration renews.</li>
 *   <li>An administrator's <em>invite</em> lapses once its admin-issued activation token has expired unredeemed, the
 *       same 24 hours counted from the last invite, so a re-invite renews it. A disabled invite never lapses: the
 *       disable expired its token, and freeing its identifiers would undo the administrator's decision.</li>
 *   <li>A pending row with no activation token at all is neither, and never lapses: the check fails closed.</li>
 * </ul>
 * A lapsed pending registration is deleted without a tombstone: it never held a credential, and a tombstone would
 * squat its identifiers for good (ADR-044). Its activation tokens go with it ({@code ON DELETE CASCADE}). The caller
 * holds its row lock and writes the audit row, catalogue row 48, once its transaction has ended.
 */
@Component
public class PendingRegistrationLapse {

    /** How long a registration holds its username, and a pending registration lives: its activation token's life. */
    public static final Duration PENDING_PERIOD = CredentialTokenType.ACTIVATION.lifetime();

    private final UserAccountRepository accounts;
    private final CredentialTokens tokens;

    PendingRegistrationLapse(UserAccountRepository accounts, CredentialTokens tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    /** Whether {@code account} is a pending registration that has lapsed at {@code now}. */
    public boolean lapsed(UserAccount account, Instant now) {
        if (!account.isPending()) {
            return false;
        }
        if (tokens.selfRegistered(account.getId())) {
            return !now.isBefore(account.getCreatedAt().plus(PENDING_PERIOD));
        }
        return account.isEnabled() && tokens.invited(account.getId())
                && !tokens.adminIssuedPending(account.getId(), CredentialTokenType.ACTIVATION);
    }

    /**
     * Deletes {@code account}, which {@link #lapsed} has just accepted under its row lock, and flushes, so an insert
     * of the same username or address later in the transaction does not meet it.
     *
     * @return its id, for the audit row
     */
    public UUID delete(UserAccount account) {
        accounts.delete(account);
        accounts.flush();
        return account.getId();
    }
}
