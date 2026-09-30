package sg.securedhello.mfa;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes an account's TOTP factor: the confirmed row, which is the enrolment (ADR-053), and any pending enrolment, so
 * an attacker's half-finished secret cannot survive the reset (ADR-049). Deleting the confirmed row clears both
 * lockout tiers with it, tier 2's disable included (ADR-027). Two callers, both inside their transaction: the guarded
 * admin factor reset, after its lock set, which already holds the {@code users} and {@code totp_user_details} rows;
 * and the offline recovery runner, which bypasses the guard by design, with the application stopped (ADR-072).
 */
@Component
public class TotpFactorRemoval {

    private final TotpUserDetailsRepository factors;
    private final PendingTotpRepository pending;

    TotpFactorRemoval(TotpUserDetailsRepository factors, PendingTotpRepository pending) {
        this.factors = factors;
        this.pending = pending;
    }

    /** Deletes {@code userId}'s confirmed and pending TOTP rows, if it has them, in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(UUID userId) {
        factors.deleteById(userId);
        pending.deleteById(userId);
    }
}
