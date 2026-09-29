package sg.securedhello.registration;

import java.io.Serial;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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
 *   <li>A username held by an account or a tombstone is refused as unavailable, unless it is the username of the very
 *       pending registration being repeated. It is checked first, so a request that collides on email never reserves
 *       a username.</li>
 *   <li>The email address is then canonicalised, and its state decides silently: a new address becomes a pending
 *       registration; a self-registered pending address is replaced, taking the new username (R-CRED-010); an
 *       activated account's address, an administrator's pending invite or a tombstoned address creates nothing.</li>
 * </ol>
 * The caller answers the same 202 in every email state (R-CRED-018). Nothing here hashes a password, so no state costs
 * a BCrypt call the others do not (T-AUTH-014). The link is sent after the transaction commits.
 */
@Service
public class Registration {

    private final UserAccountRepository accounts;
    private final Tombstones tombstones;
    private final CredentialTokens tokens;
    private final CredentialLinks links;
    private final EmailService email;
    private final TransactionTemplate transactions;
    private final Clock clock;

    Registration(UserAccountRepository accounts, Tombstones tombstones, CredentialTokens tokens,
            CredentialLinks links, EmailService email, PlatformTransactionManager transactionManager, Clock clock) {
        this.accounts = accounts;
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
     * @throws UsernameUnavailableException if another account or a tombstone holds the username
     */
    public void register(String username, String submittedEmail) {
        if (!Identifiers.validUsername(username)) {
            throw new InvalidIdentifierException();
        }
        String canonicalEmail = Identifiers.canonicalEmail(submittedEmail).orElseThrow(InvalidIdentifierException::new);
        Optional<LinkEmail> link = transactions.execute(status -> reserve(username, canonicalEmail));
        link.ifPresent(email::send);
    }

    private Optional<LinkEmail> reserve(String username, String canonicalEmail) {
        boolean available = accounts.findByUsername(username)
                .map(holder -> isSelfRegisteredPending(holder) && holder.getEmail().equals(canonicalEmail))
                .orElse(true);
        if (!available || tombstones.holdsUsername(username)) {
            throw new UsernameUnavailableException();
        }
        if (tombstones.holdsEmail(canonicalEmail)) {
            return Optional.empty();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<UserAccount> holder = accounts.findByEmail(canonicalEmail);
        if (holder.isPresent() && !isSelfRegisteredPending(holder.get())) {
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
