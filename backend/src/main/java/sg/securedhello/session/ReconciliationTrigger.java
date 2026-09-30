package sg.securedhello.session;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * The durable states that say an account should hold no session, the only triggers the reconciliation sweep can see
 * (ADR-039). A role change, a credential change or a factor reset leaves nothing to reconcile against. The order is
 * the precedence: an account in several states is counted once, under the first.
 */
public enum ReconciliationTrigger {

    /** The session's principal no longer names an account: a deletion (ADR-044). */
    DELETED,
    /** An administrator disabled the account ({@code enabled = false}). */
    DISABLED,
    /** The NIST cap disabled the password ({@code password_disabled_at} set; ADR-013). */
    CAPPED,
    /** A password lock still in force ({@code locked_until} after now; ADR-011). */
    LOCKED,
    /** The TOTP factor is under tier-2 disable ({@code factor_disabled_at} set; ADR-027). */
    FACTOR_DISABLED;

    /**
     * An account's standing as the sweep reads it.
     *
     * @param exists         a {@code users} row holds the session's principal
     * @param enabled        {@code users.enabled}
     * @param capped         {@code users.password_disabled_at} is set
     * @param lockedUntil    {@code users.locked_until}
     * @param factorDisabled {@code totp_user_details.factor_disabled_at} is set
     */
    public record Standing(boolean exists, boolean enabled, boolean capped, @Nullable Instant lockedUntil,
            boolean factorDisabled) {
    }

    /** The first trigger {@code standing} meets at {@code now}, or none for an account in good standing. */
    public static Optional<ReconciliationTrigger> of(Standing standing, Instant now) {
        return Stream.of(values()).filter(trigger -> trigger.holds(standing, now)).findFirst();
    }

    private boolean holds(Standing standing, Instant now) {
        return switch (this) {
            case DELETED -> !standing.exists();
            case DISABLED -> !standing.enabled();
            case CAPPED -> standing.capped();
            case LOCKED -> standing.lockedUntil() != null && now.isBefore(standing.lockedUntil());
            case FACTOR_DISABLED -> standing.factorDisabled();
        };
    }
}
