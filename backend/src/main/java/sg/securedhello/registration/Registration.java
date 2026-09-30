package sg.securedhello.registration;

import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.email.CredentialLinks;
import sg.securedhello.email.EmailService;
import sg.securedhello.email.LinkEmail;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UniqueIdentifierIndexes;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Self-registration, step one of two (ADR-032): it reserves a username for an email address and sends that address an
 * activation link. No password is taken here; it is set when the link is redeemed ({@link Activation}).
 *
 * <p>The username axis answers specifically, the email axis never does:
 * <ol>
 *   <li>A username that is not canonical, is malformed or is reserved is refused (ADR-045; REJ-021; REJ-027).</li>
 *   <li>A username is unavailable if an account, a tombstone or another address's live {@link UsernameHold} has it.
 *       The one exception is another address's self-registered pending registration that has outlived the
 *       {@link #PENDING_PERIOD}: it is deleted, without a tombstone, its username is free again, and the deletion is
 *       audited (row 48). An administrator's pending invite never lapses. Expired holds are purged first.</li>
 *   <li>Every registration that passes takes or renews the hold on its username for the pending period, <em>whatever
 *       state the email address is in</em>. A second registration of that username from another address is therefore
 *       refused alike whether the first address was new, pending, activated, invited or tombstoned, so the username
 *       axis never reveals the email axis (ADR-032 amendment of 2026-09-29; T-AUTH-018).</li>
 *   <li>The email address's state then decides silently: a new address becomes a pending registration; a
 *       self-registered pending address is replaced, taking the new username (R-CRED-010); an activated account's
 *       address, an administrator's pending invite or a tombstoned address creates nothing.</li>
 * </ol>
 * The caller answers the same 202 in every email state (R-CRED-018). Nothing here hashes a password, so no state costs
 * a BCrypt call the others do not (T-AUTH-014). The link is sent after the transaction commits. The audit row is
 * specific where the wire is not (ADR-032): row 16 names a new pending registration ({@code NEW_ACCOUNT}) and nothing
 * for a known address ({@code EXISTING_ADDRESS}), and a refused username writes row 17, which names nothing. Each is
 * written once its transaction has ended.
 *
 * <p>It runs as three short transactions, each of which locks at most one account row, so two registrations can never
 * wait on each other's account rows in opposite orders (T-CRED-027):
 * <ol>
 *   <li>purge the expired holds;</li>
 *   <li>delete a lapsed pending registration of another address that has the username, under its row lock, so its
 *       activation runs wholly before or after;</li>
 *   <li>reserve: lock the address's account row, the lock {@link Activation} takes too, so a re-registration and an
 *       activation of one pending registration run one after the other; check the username, which by now names no
 *       lapsed registration of another address, without locking its row; hold it; and create or replace.</li>
 * </ol>
 * A registration that interleaves with another between the steps answers as if it had run after it. The steps share
 * one instant, so the purge, the lapse and the hold check agree. {@link #register} must not run inside a caller's
 * transaction: joined into one, the steps would take two account rows again.
 */
@Service
public class Registration {

    /** How long a registration holds its username, and a pending registration lives: its activation token's life. */
    static final Duration PENDING_PERIOD = CredentialTokenType.ACTIVATION.lifetime();

    /**
     * The unique index a new address's pending registration is inserted into (V2). A concurrent registration of the
     * same new address that inserted first wins it; the loser changes nothing and answers the email axis's uniform
     * 202, as if it had run after the winner, whose link is the one the address receives. Anything else would make the
     * race an email-axis oracle (ADR-032).
     */
    private static final Set<String> UNIQUE_EMAIL_INDEX = Set.of(UniqueIdentifierIndexes.USERS_EMAIL);

    /** The unique indexes a registration inserts a username into (V2; V8). */
    private static final Set<String> UNIQUE_USERNAME_INDEXES = Set.of(UniqueIdentifierIndexes.USERNAME_HOLDS_USERNAME,
            UniqueIdentifierIndexes.USERS_USERNAME);

    private final UserAccountRepository accounts;
    private final UsernameHoldRepository holds;
    private final Tombstones tombstones;
    private final CredentialTokens tokens;
    private final CredentialLinks links;
    private final EmailService email;
    private final TransactionTemplate transactions;
    private final AuditEmitter audit;
    private final Clock clock;

    Registration(UserAccountRepository accounts, UsernameHoldRepository holds, Tombstones tombstones,
            CredentialTokens tokens, CredentialLinks links, EmailService email,
            PlatformTransactionManager transactionManager, AuditEmitter audit, Clock clock) {
        this.accounts = accounts;
        this.holds = holds;
        this.tombstones = tombstones;
        this.tokens = tokens;
        this.links = links;
        this.email = email;
        this.transactions = new TransactionTemplate(transactionManager);
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Registers {@code username} for {@code submittedEmail}.
     *
     * @throws InvalidIdentifierException   if the username or the email address is not acceptable as submitted
     * @throws UsernameUnavailableException if another account, a tombstone or another address's hold has the username
     */
    public void register(String username, String submittedEmail) {
        if (!Identifiers.validUsername(username)) {
            throw new InvalidIdentifierException();
        }
        String canonicalEmail = Identifiers.canonicalEmail(submittedEmail).orElseThrow(InvalidIdentifierException::new);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        transactions.executeWithoutResult(status -> holds.deleteExpired(now));
        transactions.execute(status -> deleteLapsed(username, canonicalEmail, now)).ifPresent(lapsed ->
                audit.emit(AuditEvent.PENDING_REGISTRATION_LAPSED, AccountContext.registrationLapsed(lapsed)));
        Reserved reserved;
        try {
            reserved = transactions.execute(status -> reserve(username, canonicalEmail, now));
        } catch (UsernameUnavailableException e) {
            audit.emit(AuditEvent.REGISTRATION_REFUSED, AccountContext.registrationRefused());
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (UniqueIdentifierIndexes.violated(e, UNIQUE_EMAIL_INDEX)) {
                audit.emit(AuditEvent.REGISTRATION_ACCEPTED, AccountContext.registrationOfExistingAddress());
                return;
            }
            RuntimeException refusal = usernameRace(e);
            if (refusal instanceof UsernameUnavailableException) {
                audit.emit(AuditEvent.REGISTRATION_REFUSED, AccountContext.registrationRefused());
            }
            throw refusal;
        }
        audit.emit(AuditEvent.REGISTRATION_ACCEPTED, reserved.created()
                .map(AccountContext::registrationCreated)
                .orElseGet(AccountContext::registrationOfExistingAddress));
        reserved.link().ifPresent(email::send);
    }

    /**
     * What a reservation did: the new pending registration it created, if any, and the activation link to send, if
     * any. A renewed pending registration gets a link but is not new.
     */
    private record Reserved(Optional<UUID> created, Optional<LinkEmail> link) {
    }

    /**
     * A concurrent registration of the same username committed first, so this one's insert hit a unique username
     * index: it is the same 400 as a username found taken. Any other integrity failure stays a failure.
     */
    private static RuntimeException usernameRace(DataIntegrityViolationException e) {
        return UniqueIdentifierIndexes.violated(e, UNIQUE_USERNAME_INDEXES) ? new UsernameUnavailableException() : e;
    }

    /**
     * Deletes the pending registration that has {@code username}, if it is another address's, self-registered, and has
     * outlived the pending period, and returns its id. Its row is locked first, so an activation of it runs wholly
     * before this, and then it is no longer pending.
     */
    private Optional<UUID> deleteLapsed(String username, String canonicalEmail, Instant now) {
        return accounts.findForUpdateByUsername(username)
                .filter(account -> !account.getEmail().equals(canonicalEmail))
                .filter(account -> !now.isBefore(account.getCreatedAt().plus(PENDING_PERIOD)))
                .filter(this::isSelfRegisteredPending)
                .map(account -> {
                    accounts.delete(account);
                    return account.getId();
                });
    }

    private Reserved reserve(String username, String canonicalEmail, Instant now) {
        // Locked before anything is read, so an activation of this address's registration runs wholly before or after.
        Optional<UserAccount> holder = accounts.findForUpdateByEmail(canonicalEmail);
        UsernameHold hold = holdUsername(username, canonicalEmail, now);
        hold.renew(canonicalEmail, now.plus(PENDING_PERIOD));
        holds.save(hold);
        if (tombstones.holdsEmail(canonicalEmail)
                || holder.isPresent() && !isSelfRegisteredPending(holder.get())) {
            return new Reserved(Optional.empty(), Optional.empty());
        }
        UserAccount account = holder.orElseGet(() -> UserAccount.pendingRegistration(username, canonicalEmail, now));
        if (holder.isPresent()) {
            account.replacePendingRegistration(username, now);
        } else {
            accounts.save(account);
        }
        String token = tokens.mint(account.getId(), CredentialTokenType.ACTIVATION);
        return new Reserved(holder.isPresent() ? Optional.empty() : Optional.of(account.getId()),
                Optional.of(links.email(CredentialTokenType.ACTIVATION, canonicalEmail, token)));
    }

    /**
     * The hold {@code canonicalEmail} may take on {@code username}: the existing one if it is this address's or has run
     * out, or a new one. The account the username names is read without its row lock: if it is this address's
     * self-registered pending registration, the caller holds that lock already; any other account makes the username
     * unavailable, a lapsed one included, which {@link #deleteLapsed} leaves only if a race kept it from seeing it.
     *
     * @throws UsernameUnavailableException if an account, a tombstone or another address's live hold has the username
     */
    private UsernameHold holdUsername(String username, String canonicalEmail, Instant now) {
        Optional<UserAccount> named = accounts.findByUsername(username);
        if (named.isPresent() && !(named.get().getEmail().equals(canonicalEmail)
                && isSelfRegisteredPending(named.get()))) {
            throw new UsernameUnavailableException();
        }
        if (tombstones.holdsUsername(username)) {
            throw new UsernameUnavailableException();
        }
        Optional<UsernameHold> held = holds.findByUsername(username);
        if (held.isPresent() && !held.get().getEmail().equals(canonicalEmail) && held.get().liveAt(now)) {
            throw new UsernameUnavailableException();
        }
        return held.orElseGet(() -> new UsernameHold(username, canonicalEmail, now.plus(PENDING_PERIOD)));
    }

    /**
     * A pending registration a repeated self-registration may replace: one whose activation token was self-issued. An
     * administrator's pending invite is not one, whatever its role: its token carries the explicit admin-issued marker,
     * and replacing it would let a stranger rename the invited account and cancel the token the administrator handed
     * on (ADR-007 amendment).
     */
    private boolean isSelfRegisteredPending(UserAccount account) {
        return account.isPending() && tokens.selfRegistered(account.getId());
    }

    /** The username or the email address is not acceptable as submitted: 400 {@code VALIDATION_FAILED}. */
    public static final class InvalidIdentifierException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        InvalidIdentifierException() {
            super("The username or email address is not acceptable");
        }
    }

    /** The username is taken: 400 {@code VALIDATION_FAILED} with rule {@code USERNAME_UNAVAILABLE} (ADR-032). */
    public static final class UsernameUnavailableException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        UsernameUnavailableException() {
            super("The username is unavailable");
        }
    }
}
