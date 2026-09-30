package sg.securedhello.security.lockout;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import sg.securedhello.audit.LockoutReason;
import sg.securedhello.audit.LoginFailureReason;
import sg.securedhello.audit.PasswordDisableReason;
import sg.securedhello.security.lockout.LockoutCounter.Outcome;
import sg.securedhello.security.ratelimit.LockoutCardinality;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.security.source.SourceKeyAuthenticationDetails;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.SignedInUser;
import sg.securedhello.user.TrustedDevice;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Moves the password counters on the provider's outcomes, in the sign-in's lane, and writes the lockout rows (ADR-011;
 * ADR-012; ADR-013; ADR-075).
 *
 * <ul>
 *   <li><b>A wrong password</b> ({@code AuthenticationFailureBadCredentialsEvent}) counts on the account, if it
 *       exists: in the device's own lane if the sign-in presented a valid device cookie for it
 *       ({@link LockoutLane}), otherwise in the account's untrusted lane, and on the NIST cap either way. Nothing else
 *       moves a counter: a locked, disabled, capped or expired account is refused by a lock or pre-authentication
 *       check with another exception, and a limiter refusal never reaches the provider (ADR-010; ADR-046).</li>
 *   <li><b>A correct password</b> ({@code AuthenticationSuccessEvent} for a password token) resets its lane's counters
 *       and the cap counter; a trusted device's also resets the untrusted lane's windowed counter. Only the password
 *       stage resets the cap: a factor success is another authenticator (ADR-013).</li>
 * </ul>
 *
 * <p>Each outcome is counted under the account's row lock, so concurrent failures are counted one after another and
 * none is lost (R-DATA-014). The account's device rows are read and written under the same lock. The rows are written
 * after commit. A lockout in either lane also records the account in its source's lockout-cardinality set, the source
 * read from the token's {@link SourceKeyAuthenticationDetails}, failing closed to {@link SourceKey#UNPARSEABLE} when
 * they are missing (ADR-015). Every lock counts, whichever rule fired it, so paced failures that lock under the
 * consecutive rule enter the axis too.
 *
 * <p>A failed login below the threshold never touches the account's sessions (ADR-034). The failure that locks a
 * trusted device, or disables the password, ends all of them (ADR-037): once, on the transition, registered inside the
 * row-lock transaction so the session-termination service runs it after commit (ADR-039). An untrusted-lane lock ends
 * none: anyone who knows the username can cause one, and it no longer refuses the owner's trusted device (ADR-075).
 *
 * <p>A failure whose row lock is not granted in time is still the uniform 401 and is still counted (ADR-011
 * amendment of 2026-09-30): it is deferred, with its own time, and counted before anything newer by the next outcome
 * for that account that takes the lock. A deferred failure has lost its lane, so it counts in the untrusted lane and
 * on the cap.
 */
final class LockoutRecorder {

    private static final Logger log = LoggerFactory.getLogger(LockoutRecorder.class);

    private final UserAccountRepository accounts;
    private final TrustedDeviceRepository devices;
    private final TransactionTemplate transactions;
    private final LockoutCounter counter;
    private final LockoutCardinality cardinality;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final Clock clock;
    private final DeferredFailures deferred = new DeferredFailures();

    LockoutRecorder(UserAccountRepository accounts, TrustedDeviceRepository devices, TransactionTemplate transactions,
            LockoutCounter counter, LockoutCardinality cardinality, SessionTerminationService sessions,
            AuditEmitter audit, Clock clock) {
        this.accounts = accounts;
        this.devices = devices;
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
        Object details = event.getAuthentication().getDetails();
        // The columns are TIMESTAMP(6): count on the stored precision, so a window compares what was written.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Instant> due = deferred.take(username);
        List<Instant> retained = new ArrayList<>(due);
        retained.add(now);
        List<Counted> counted;
        try {
            counted = keepingDeferred(username, retained, () -> transactions.execute(status -> accounts
                    .findForUpdateByUsername(username)
                    .filter(account -> account.getPasswordHash() != null)
                    .map(account -> {
                        List<Counted> results = failures(account, due);
                        results.add(failure(account, trustedDevice(details, account, now), now));
                        return endSessionsIfRestricted(username, results);
                    })
                    .orElse(List.of())));
        } catch (PessimisticLockingFailureException contended) {
            // Contended (ADR-011): the sign-in has already failed and still gets the uniform 401, never a 500, and
            // the failure is not lost: it waits, with its own time, for the next outcome that takes the row lock.
            log.warn("A wrong-password failure is deferred: the account row lock was not granted in time");
            return;
        }
        settle(counted, username, details);
    }

    /**
     * A correct password resets its lane's counters, once any deferred failures are counted first. If the password is
     * then disabled, or the sign-in's lane locked (deferred failures lock only the untrusted lane), the success is not
     * counted: this sign-in is refused with the uniform 401 ({@link DeferredFailuresRefusal}), as it would have been
     * had they been counted in time, and a disable ends the account's sessions.
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
                    List<Counted> results = endSessionsIfRestricted(username, failures(account, due));
                    Optional<TrustedDevice> device = trustedDevice(token.getDetails(), account, now);
                    LoginFailureReason refusal = refusal(account.getLockoutState(), device, now);
                    if (refusal == null) {
                        results.add(device
                                .map(trusted -> count(account, trusted,
                                        counter.success(account.getLockoutState(), trusted.getLockState())))
                                .orElseGet(() -> count(account, counter.success(account.getLockoutState()))));
                    }
                    return new Success(results, refusal);
                })
                .orElse(new Success(List.of(), null))));
        settle(success.counted(), username, token.getDetails());
        if (success.refusal() != null) {
            throw new DeferredFailuresRefusal(user.id(), success.refusal());
        }
    }

    /**
     * Why a correct password is refused at {@code now}, read under the row lock: the password disabled, or its lane's
     * lock running (the trusted device's own, or else the untrusted lane's), which deferred failures or a race past
     * the lock check can have set; or null.
     */
    private static @Nullable LoginFailureReason refusal(PasswordLockoutState state, Optional<TrustedDevice> device,
            Instant now) {
        if (state.passwordDisabled()) {
            return LoginFailureReason.PASSWORD_DISABLED;
        }
        boolean locked = device.map(trusted -> trusted.getLockState().lockedAt(now)).orElse(state.lockedAt(now));
        return locked ? LoginFailureReason.ACCOUNT_LOCKED : null;
    }

    /** A correct password's counting: the rows to write, and why deferred failures refuse it, if they do. */
    private record Success(List<Counted> counted, @Nullable LoginFailureReason refusal) {
    }

    /**
     * The trusted device a sign-in with {@code details} counts in on {@code account}, which the caller holds locked:
     * the device its lane names, if that device is still the account's and trusted. A device revoked since the lane was
     * decided is gone, so the sign-in falls back to the untrusted lane.
     */
    private Optional<TrustedDevice> trustedDevice(@Nullable Object details, UserAccount account, Instant now) {
        LockoutLane lane = LockoutLane.of(details, account.getId(), now);
        return lane.trusted()
                ? devices.findById(lane.device())
                        .filter(device -> device.getUserId().equals(account.getId()) && device.trustedAt(now))
                : Optional.empty();
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

    /**
     * Counts each deferred failure in {@code due}, oldest first, in {@code account}'s untrusted lane; the caller holds
     * the account locked. A mutable list, for the caller to add to.
     */
    private List<Counted> failures(UserAccount account, List<Instant> due) {
        List<Counted> results = new ArrayList<>();
        for (Instant failedAt : due) {
            results.add(count(account, counter.failure(account.getLockoutState(), failedAt)));
        }
        return results;
    }

    /** Counts one wrong password at {@code now}, in {@code device}'s lane if there is one, else the untrusted lane. */
    private Counted failure(UserAccount account, Optional<TrustedDevice> device, Instant now) {
        return device
                .map(trusted -> count(account, trusted,
                        counter.failure(account.getLockoutState(), trusted.getLockState(), now)))
                .orElseGet(() -> count(account, counter.failure(account.getLockoutState(), now)));
    }

    /**
     * The source the login converter put on the token. Missing or foreign details fail closed (ADR-015): the lockout is
     * attributed to the one {@link SourceKey#UNPARSEABLE} key, whose set then fills and refuses, rather than the axis
     * switching off unseen.
     */
    static SourceKey sourceOf(@Nullable Object details) {
        return details instanceof SourceKeyAuthenticationDetails source ? source.sourceKey() : SourceKey.UNPARSEABLE;
    }

    /**
     * Ends the account's sessions if an outcome disabled its password or locked a trusted device (ADR-037). An
     * untrusted-lane lock ends none (ADR-075). Returns {@code counted}.
     */
    private List<Counted> endSessionsIfRestricted(String username, List<Counted> counted) {
        if (counted.stream().map(Counted::outcome)
                .anyMatch(outcome -> outcome.disabled() || outcome.locked() && outcome.trusted())) {
            sessions.endAll(username);
        }
        return counted;
    }

    private static Counted count(UserAccount account, Outcome outcome) {
        if (outcome.changed(account.getLockoutState())) {
            account.setLockoutState(outcome.state());
        }
        return new Counted(account.getId(), outcome);
    }

    private static Counted count(UserAccount account, TrustedDevice device, Outcome outcome) {
        if (!device.getLockState().equals(outcome.device())) {
            device.setLockState(outcome.device());
        }
        return count(account, outcome);
    }

    private void report(Counted counted) {
        UUID userId = counted.userId();
        Outcome outcome = counted.outcome();
        if (outcome.lockCleared()) {
            audit.emit(AuditEvent.LOCKOUT_CLEARED, AccountContext.lockCleared(userId,
                    outcome.trusted() ? LockoutClearReason.TRUSTED_DEVICE_AUTO_LIFT : LockoutClearReason.AUTO_LIFT));
        }
        if (outcome.locked()) {
            audit.emit(AuditEvent.LOCKOUT_TRIGGERED, AccountContext.lockout(userId,
                    outcome.trusted() ? LockoutReason.TRUSTED_DEVICE_THRESHOLD_REACHED
                            : LockoutReason.THRESHOLD_REACHED));
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
