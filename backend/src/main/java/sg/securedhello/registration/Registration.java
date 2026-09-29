package sg.securedhello.registration;

import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.email.CredentialLinks;
import sg.securedhello.email.EmailService;
import sg.securedhello.email.LinkEmail;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;
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
 *       The one exception is a self-registered pending registration that has outlived the {@link #PENDING_PERIOD}:
 *       it is deleted, and its username is free again.</li>
 *   <li>Every registration that passes takes or renews the hold on its username for the pending period, <em>whatever
 *       state the email address is in</em>. A second registration of that username from another address is therefore
 *       refused alike whether the first address was new, pending, activated, invited or tombstoned, so the username
 *       axis never reveals the email axis (ADR-032 amendment of 2026-09-29; T-AUTH-018).</li>
 *   <li>The email address's state then decides silently: a new address becomes a pending registration; a
 *       self-registered pending address is replaced, taking the new username (R-CRED-010); an activated account's
 *       address, an administrator's pending invite or a tombstoned address creates nothing.</li>
 * </ol>
 * The caller answers the same 202 in every email state (R-CRED-018). Nothing here hashes a password, so no state costs
 * a BCrypt call the others do not (T-AUTH-014). The link is sent after the transaction commits.
 *
 * <p>The address's account row is locked first, the lock {@link Activation} takes too, so a re-registration and an
 * activation of one pending registration run one after the other (T-CRED-027).
 */
@Service
public class Registration {

    /** How long a registration holds its username, and a pending registration lives: its activation token's life. */
    static final Duration PENDING_PERIOD = CredentialTokenType.ACTIVATION.lifetime();

    /** The unique indexes a registration inserts a username into (V2; V8). */
    private static final List<String> UNIQUE_USERNAME_INDEXES = List.of("UX_USERNAME_HOLDS_USERNAME",
            "UX_USERS_USERNAME");

    private final UserAccountRepository accounts;
    private final UsernameHoldRepository holds;
    private final Tombstones tombstones;
    private final CredentialTokens tokens;
    private final CredentialLinks links;
    private final EmailService email;
    private final TransactionTemplate transactions;
    private final Clock clock;

    Registration(UserAccountRepository accounts, UsernameHoldRepository holds, Tombstones tombstones,
            CredentialTokens tokens, CredentialLinks links, EmailService email,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.accounts = accounts;
        this.holds = holds;
        this.tombstones = tombstones;
        this.tokens = tokens;
        this.links = links;
        this.email = email;
        this.transactions = new TransactionTemplate(transactionManager);
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
        Optional<LinkEmail> link;
        try {
            link = transactions.execute(status -> reserve(username, canonicalEmail));
        } catch (DataIntegrityViolationException e) {
            throw usernameRace(e);
        }
        link.ifPresent(email::send);
    }

    /**
     * A concurrent registration of the same username committed first, so this one's insert hit a unique username
     * index: it is the same 400 as a username found taken. Any other integrity failure stays a failure.
     */
    private static RuntimeException usernameRace(DataIntegrityViolationException e) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                .toUpperCase(Locale.ROOT);
        return UNIQUE_USERNAME_INDEXES.stream().anyMatch(cause::contains) ? new UsernameUnavailableException() : e;
    }

    private Optional<LinkEmail> reserve(String username, String canonicalEmail) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        holds.deleteExpired(now);
        // Locked before anything is read, so an activation of this address's registration runs wholly before or after.
        Optional<UserAccount> holder = accounts.findForUpdateByEmail(canonicalEmail);
        UsernameHold hold = holdUsername(username, canonicalEmail, now);
        hold.renew(canonicalEmail, now.plus(PENDING_PERIOD));
        holds.save(hold);
        if (tombstones.holdsEmail(canonicalEmail)
                || holder.isPresent() && !isSelfRegisteredPending(holder.get())) {
            return Optional.empty();
        }
        UserAccount account = holder.orElseGet(() -> UserAccount.pendingRegistration(username, canonicalEmail, now));
        if (holder.isPresent()) {
            account.replacePendingRegistration(username, now);
        } else {
            accounts.save(account);
        }
        String token = tokens.mint(account.getId(), CredentialTokenType.ACTIVATION);
        return Optional.of(links.email(CredentialTokenType.ACTIVATION, canonicalEmail, token));
    }

    /**
     * The hold {@code canonicalEmail} may take on {@code username}: the existing one if it is this address's, or a new
     * one. A pending registration of another address that has outlived the pending period is deleted, which frees its
     * username.
     *
     * @throws UsernameUnavailableException if an account, a tombstone or another address's live hold has the username
     */
    private UsernameHold holdUsername(String username, String canonicalEmail, Instant now) {
        // Under the row lock, so an activation of a pending registration about to lapse runs wholly before this.
        Optional<UserAccount> named = accounts.findForUpdateByUsername(username);
        if (named.isPresent()) {
            UserAccount account = named.get();
            boolean pending = isSelfRegisteredPending(account);
            boolean ours = pending && account.getEmail().equals(canonicalEmail);
            boolean lapsed = pending && !now.isBefore(account.getCreatedAt().plus(PENDING_PERIOD));
            if (!ours && !lapsed) {
                throw new UsernameUnavailableException();
            }
            if (!ours) {
                accounts.delete(account);
                accounts.flush();
            }
        }
        if (tombstones.holdsUsername(username)) {
            throw new UsernameUnavailableException();
        }
        Optional<UsernameHold> held = holds.findByUsername(username);
        if (held.isPresent() && !held.get().getEmail().equals(canonicalEmail)) {
            throw new UsernameUnavailableException();
        }
        return held.orElseGet(() -> new UsernameHold(username, canonicalEmail, now.plus(PENDING_PERIOD)));
    }

    /**
     * A pending registration a repeated self-registration may replace. An administrator's pending invite is not one:
     * its token was handed to the administrator, and replacing it would let a stranger rename an invited account.
     */
    private static boolean isSelfRegisteredPending(UserAccount account) {
        return account.isPending() && "USER".equals(account.getRole());
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
