package sg.securedhello.security.lockout;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.LockoutClearReason;
import sg.securedhello.audit.LoginFailureReason;
import sg.securedhello.audit.PasswordDisableReason;
import sg.securedhello.security.lockout.LockoutCounter.Outcome;
import sg.securedhello.security.ratelimit.LockoutCardinality;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.security.source.SourceKeyAuthenticationDetails;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.SignedInUser;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Moves the password counters on the provider's outcomes, and writes the lockout rows (ADR-011; ADR-012; ADR-013).
 *
 * <ul>
 *   <li><b>A wrong password</b> ({@code AuthenticationFailureBadCredentialsEvent}) counts on the account, if it
 *       exists. Nothing else moves a counter: a locked, disabled, capped or expired account is refused by a
 *       pre-authentication check with another exception, and a limiter refusal never reaches the provider
 *       (ADR-010; ADR-046).</li>
 *   <li><b>A correct password</b> ({@code AuthenticationSuccessEvent} for a password token) resets both counters. Only
 *       the password stage resets the cap: a factor success is another authenticator (ADR-013).</li>
 * </ul>
 *
 * <p>Each outcome is counted under the account's row lock, so concurrent failures are counted one after another and
 * none is lost (R-DATA-014). The rows are written after commit. A lockout also records the account in its source's
 * lockout-cardinality set, the source read from the token's {@link SourceKeyAuthenticationDetails}, failing closed to
 * {@link SourceKey#UNPARSEABLE} when they are missing (ADR-015). Every lock counts, whichever rule fired it, so paced
 * failures that lock under the consecutive rule enter the axis too.
 *
 * <p>A failed login below the threshold never touches the account's sessions (ADR-034). The failure that locks the
 * account or disables its password ends all of them (ADR-037): once, on the transition, registered inside the row-lock
 * transaction so the session-termination service runs it after commit (ADR-039).
 *
 * <p>A failure whose row lock is not granted in time is still the uniform 401 and is still counted (ADR-011
 * amendment of 2026-09-30): it is deferred, with its own time, and counted before anything newer by the next outcome
 * for that account that takes the lock.
 */
final class LockoutRecorder {

    private static final Logger log = LoggerFactory.getLogger(LockoutRecorder.class);

    private final UserAccountRepository accounts;
    private final TransactionTemplate transactions;
    private final LockoutCounter counter;
    private final LockoutCardinality cardinality;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final Clock clock;
    private final DeferredFailures deferred = new DeferredFailures();

    LockoutRecorder(UserAccountRepository accounts, TransactionTemplate transactions, LockoutCounter counter,
            LockoutCardinality cardinality, SessionTerminationService sessions, AuditEmitter audit, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.counter = counter;
        this.cardinality = cardinality;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    void wrongPassword(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        // The columns are TIMESTAMP(6): count on the stored precision, so a window compares what was written.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Instant> due = deferred.take(username);
        due.add(now);
        List<Counted> counted;
        try {
            counted = keepingDeferred(username, due, () -> transactions.execute(status -> accounts
                    .findForUpdateByUsername(username)
                    .filter(account -> account.getPasswordHash() != null)
                    .map(account -> endSessionsIfRestricted(username, failures(account, due)))
                    .orElse(List.of())));
        } catch (PessimisticLockingFailureException contended) {
            // Contended (ADR-011): the sign-in has already failed and still gets the uniform 401, never a 500, and
            // the failure is not lost: it waits, with its own time, for the next outcome that takes the row lock.
            log.warn("A wrong-password failure is deferred: the account row lock was not granted in time");
            return;
        }
        settle(counted, username, event.getAuthentication().getDetails());
    }

    /**
     * A correct password resets both counters, once any deferred failures are counted first. If those failures lock
     * the account or disable its password, the success is not counted: the account's sessions end, and this sign-in
     * is refused with the uniform 401 ({@link DeferredFailuresRefusal}), as it would have been had they been counted
     * in time.
     */
    @EventListener
    void rightPassword(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token)
                || !(token.getPrincipal() instanceof SignedInUser user)) {
            return;
        }
        String username = user.getUsername();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Instant> due = deferred.take(username);
        Success success = keepingDeferred(username, due, () -> transactions.execute(status -> accounts
                .findForUpdateById(user.id())
                .map(account -> {
                    List<Counted> results = new ArrayList<>(failures(account, due));
                    LoginFailureReason refusal = refusal(account.getLockoutState(), now);
                    if (refusal != null) {
                        sessions.endAll(username);
                    } else {
                        results.add(count(account, counter.success(account.getLockoutState())));
                    }
                    return new Success(results, refusal);
                })
                .orElse(new Success(List.of(), null))));
        settle(success.counted(), username, token.getDetails());
        if (success.refusal() != null) {
            throw new DeferredFailuresRefusal(user.id(), success.refusal());
        }
    }

    /** Why {@code state} refuses a sign-in at {@code now}: the password disabled, or a lock still running; or null. */
    private static @Nullable LoginFailureReason refusal(PasswordLockoutState state, Instant now) {
        if (state.passwordDisabledAt() != null) {
            return LoginFailureReason.PASSWORD_DISABLED;
        }
        return state.lockedUntil() != null && state.lockedUntil().isAfter(now) ? LoginFailureReason.ACCOUNT_LOCKED
                : null;
    }

    /** A correct password's counting: the rows to write, and why deferred failures refuse it, if they do. */
    private record Success(List<Counted> counted, @Nullable LoginFailureReason refusal) {
    }

    /**
     * Runs {@code work}, which counts {@code due} under the row lock; if it fails in any way, {@code due} goes back to
     * the deferred failures, so no failure is lost, and the failure propagates.
     */
    private <T> T keepingDeferred(String username, List<Instant> due, Supplier<T> work) {
        try {
            return work.get();
        } catch (RuntimeException failed) {
            deferred.put(username, due);
            throw failed;
        }
    }

    /** Writes the rows for {@code counted}, and records a lockout in its source's cardinality set (ADR-015). */
    private void settle(List<Counted> counted, String username, @Nullable Object details) {
        counted.forEach(this::report);
        if (counted.stream().anyMatch(result -> result.outcome().locked())) {
            cardinality.recordLockout(sourceOf(details), username);
        }
    }

    /** Counts each failure in {@code due}, oldest first, on {@code account}, which the caller holds locked. */
    private List<Counted> failures(UserAccount account, List<Instant> due) {
        List<Counted> results = new ArrayList<>();
        for (Instant failedAt : due) {
            results.add(count(account, counter.failure(account.getLockoutState(), failedAt)));
        }
        return results;
    }

    /**
     * The source the login converter put on the token. Missing or foreign details fail closed (ADR-015): the lockout is
     * attributed to the one {@link SourceKey#UNPARSEABLE} key, whose set then fills and refuses, rather than the axis
     * switching off unseen.
     */
    static SourceKey sourceOf(@Nullable Object details) {
        return details instanceof SourceKeyAuthenticationDetails source ? source.sourceKey() : SourceKey.UNPARSEABLE;
    }

    private List<Counted> endSessionsIfRestricted(String username, List<Counted> counted) {
        if (counted.stream().anyMatch(result -> result.outcome().locked() || result.outcome().disabled())) {
            sessions.endAll(username);
        }
        return counted;
    }

    private static Counted count(UserAccount account, Outcome outcome) {
        PasswordLockoutState before = account.getLockoutState();
        if (outcome.changed(before)) {
            account.setLockoutState(outcome.state());
        }
        return new Counted(account.getId(), outcome);
    }

    private void report(Counted counted) {
        UUID userId = counted.userId();
        Outcome outcome = counted.outcome();
        if (outcome.lockCleared()) {
            audit.emit(AuditEvent.LOCKOUT_CLEARED, AccountContext.lockCleared(userId, LockoutClearReason.AUTO_LIFT));
        }
        if (outcome.locked()) {
            audit.emit(AuditEvent.LOCKOUT_TRIGGERED, AccountContext.lockout(userId));
        }
        if (outcome.alerted()) {
            audit.emit(AuditEvent.PASSWORD_FAILURE_ALERT, AccountContext.passwordFailureAlert(userId));
        }
        if (outcome.disabled()) {
            audit.emit(AuditEvent.PASSWORD_DISABLED,
                    AccountContext.passwordDisabled(userId, PasswordDisableReason.FAILURE_CAP));
        }
    }

    private record Counted(UUID userId, Outcome outcome) {
    }

    /**
     * Wrong-password failures whose row lock was not granted in time, by username, each with the time it happened.
     * They are counted, in order and before anything newer, by the next outcome for that account that takes the lock.
     * An entry exists only while its row is contended, so the map stays small; it is in memory, like every other
     * throttle, on the one supported instance (REJ-018), and a restart loses it.
     */
    static final class DeferredFailures {

        /**
         * The most failures held for one username. The NIST cap is 100 consecutive failures; past it the password is
         * disabled whatever follows, so more would change nothing and only cost memory.
         */
        static final int MAXIMUM_PER_USERNAME = 100;

        private final Map<String, List<Instant>> byUsername = new ConcurrentHashMap<>();

        /** Removes and returns {@code username}'s deferred failures, oldest first; a new, mutable list. */
        List<Instant> take(String username) {
            List<Instant> taken = byUsername.remove(username);
            return taken == null ? new ArrayList<>() : new ArrayList<>(taken);
        }

        /** Defers {@code failures} for {@code username}, merged in time order with any already waiting. */
        void put(String username, List<Instant> failures) {
            byUsername.merge(username, failures.stream().sorted().limit(MAXIMUM_PER_USERNAME).toList(),
                    (waiting, more) -> Stream.concat(waiting.stream(), more.stream()).sorted()
                            .limit(MAXIMUM_PER_USERNAME).toList());
        }
    }
}
