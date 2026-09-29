package sg.securedhello.mfa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.mfa.TotpEnrolment.InvalidFactorException;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Factor verification (ADR-021): a code checked against the enrolled secret, which grants or renews
 * {@code FACTOR_TOTP}. The account is the signed-in one, never one named in the request (ADR-027).
 *
 * <p>It takes the {@code users} row lock, then the TOTP row lock, the lock order everywhere (spec, Sessions), and holds
 * the TOTP row across load, decrypt, compare and write, so two requests carrying one code cannot both verify. A code
 * is accepted only for a counter after the last accepted one, so a verified code, and every earlier one, is a replay
 * (R-MFA-017; RFC 6238 §5.2). A lock timeout fails the request; it never grants.
 *
 * <p>Every wrong code counts on the two-tier lockout under the same lock (ADR-027; {@link TotpUserDetails}); an empty
 * code is refused without counting (R-STD-043). A locked factor is refused before any code is checked, and the failure
 * that locks it is answered the same way. The failure that disables it also forces a password change and ends the
 * account's sessions after commit (ADR-016; ADR-037; ADR-039). The counts commit whatever the answer.
 */
@Service
public class TotpVerification {

    private final UserAccountRepository accounts;
    private final TotpUserDetailsRepository factors;
    private final TotpSecretCipher cipher;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    TotpVerification(UserAccountRepository accounts, TotpUserDetailsRepository factors, TotpSecretCipher cipher,
            SessionTerminationService sessions, AuditEmitter audit, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.factors = factors;
        this.cipher = cipher;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies {@code code} for {@code userId} and records its counter as used.
     *
     * @throws FactorEnrolmentRequiredException when the account has no enrolled factor
     * @throws FactorDisabledException          when tier 2 has disabled the factor, before any code is checked, or
     *                                          this failure disabled it
     * @throws FactorLockedException            when tier 1 has locked the factor, before any code is checked, or this
     *                                          failure locked it
     * @throws InvalidFactorException           when the code does not verify, including a replay
     */
    public void verify(UUID userId, String code) {
        // The lockout columns are TIMESTAMP(6): count on the stored precision, so a window compares what was written.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Checked checked = transactions.execute(status -> check(userId, code, now));
        AccountContext account = AccountContext.of(userId);
        switch (checked.outcome()) {
            case VERIFIED -> audit.emit(AuditEvent.TOTP_VERIFIED, account);
            case LOCKED -> throw new FactorLockedException(now, checked.lockedUntil());
            case WRONG -> {
                audit.emit(AuditEvent.TOTP_VERIFICATION_FAILED, account);
                throw new InvalidFactorException();
            }
            case LOCKING -> {
                audit.emit(AuditEvent.TOTP_VERIFICATION_FAILED, account);
                audit.emit(AuditEvent.TOTP_FACTOR_LOCKED, account);
                throw new FactorLockedException(now, checked.lockedUntil());
            }
            case DISABLING -> {
                audit.emit(AuditEvent.TOTP_VERIFICATION_FAILED, account);
                audit.emit(AuditEvent.TOTP_FACTOR_DISABLED, account);
                throw new FactorDisabledException();
            }
        }
    }

    private Checked check(UUID userId, String code, Instant now) {
        UserAccount account = accounts.findForUpdateById(userId)
                .orElseThrow(() -> new IllegalStateException("No account " + userId));
        TotpUserDetails factor = factors.findForUpdateByUserId(userId)
                .orElseThrow(FactorEnrolmentRequiredException::new);
        if (factor.isDisabled()) {
            throw new FactorDisabledException();
        }
        Optional<Instant> locked = factor.lockedUntil(now);
        if (locked.isPresent()) {
            return new Checked(Outcome.LOCKED, locked.get());
        }
        if (code.isEmpty()) {
            return new Checked(Outcome.WRONG, null);
        }
        byte[] secret = cipher.open(userId, factor.getKeyVersion(), factor.getTotpKey());
        OptionalLong counter = TotpWindow.match(secret, code, now, factor.lastUsedCounter());
        if (counter.isPresent()) {
            factor.accept(counter.getAsLong());
            return new Checked(Outcome.VERIFIED, null);
        }
        return switch (factor.fail(now)) {
            case COUNTED -> new Checked(Outcome.WRONG, null);
            case LOCKED -> new Checked(Outcome.LOCKING, now.plus(TotpUserDetails.LOCK));
            case DISABLED -> {
                account.requirePasswordChange();
                sessions.endAll(account.getUsername());
                yield new Checked(Outcome.DISABLING, null);
            }
        };
    }

    /** What one verification found, decided under the row locks. */
    private enum Outcome {
        VERIFIED,
        /** A wrong code, counted unless empty. */
        WRONG,
        /** The factor was already locked; nothing was checked. */
        LOCKED,
        /** A wrong code that locked the factor. */
        LOCKING,
        /** A wrong code that disabled the factor. */
        DISABLING
    }

    /** @param lockedUntil the tier-1 lock's end, for {@code LOCKED} and {@code LOCKING} */
    private record Checked(Outcome outcome, @Nullable Instant lockedUntil) {
    }

    /** A factor tier 2 disabled: 423 {@code FACTOR_DISABLED} until it is rebound (ADR-027; R-MFA-006). */
    public static final class FactorDisabledException extends RuntimeException {

        FactorDisabledException() {
            super("The TOTP factor is disabled");
        }
    }

    /**
     * A factor under a tier-1 lock: 429 {@code TOO_MANY_REQUESTS} with the factor member, {@code reason: LOCKED} and an
     * integer {@code Retry-After} taken from the lock's end (ADR-027; ADR-033).
     */
    public static final class FactorLockedException extends RuntimeException {

        private final long retryAfterSeconds;

        FactorLockedException(Instant now, @Nullable Instant lockedUntil) {
            super("The TOTP factor is locked");
            long millis = lockedUntil == null ? 0 : Duration.between(now, lockedUntil).toMillis();
            this.retryAfterSeconds = Math.max(1, Math.ceilDiv(millis, 1000));
        }

        /** Whole seconds until the lock lifts, rounded up, never 0. */
        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** An administrator with no enrolled factor: 422 {@code FACTOR_ENROLMENT_REQUIRED} (R-MFA-002). */
    public static final class FactorEnrolmentRequiredException extends RuntimeException {

        FactorEnrolmentRequiredException() {
            super("No TOTP factor is enrolled");
        }
    }
}
