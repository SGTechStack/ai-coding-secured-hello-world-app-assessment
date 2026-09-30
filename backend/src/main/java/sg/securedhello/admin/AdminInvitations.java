package sg.securedhello.admin;

import java.io.Serial;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AdminActionContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UniqueIdentifierIndexes;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Admin create, by invite (ADR-006; PRD Story 8): an administrator creates a <em>pending registration</em> with the
 * role they choose and is returned its activation token once, to pass on. The user sets their own password by
 * redeeming it through the ordinary activation path (ADR-032), so no password is taken or generated here, and no
 * forced-change flag is set (R-ADM-012).
 *
 * <p>Unlike self-registration, the answer is specific: a username or email address held by any account, pending or
 * not, or by a tombstone, is 400 {@code USER_EXISTS} and creates nothing (R-ADM-005; ADR-044). The caller is an
 * administrator, so there is nothing to hide from them.
 *
 * <p>The one exception is a <em>re-invite</em> (ADR-007 amendment): when both identifiers name the same enabled,
 * never-activated account that an administrator invited (its admin-issued activation token says so, never its role),
 * the invite is issued again on that row. Its outstanding token is cancelled and a new one minted in the same
 * transaction, the account takes the role now asked for, and the answer is the same 201. A self-registered pending
 * account, an activated or disabled one, or identifiers naming two different accounts stay {@code USER_EXISTS}.
 *
 * <p>An invite has no subject yet, so neither guard check can apply, and it does not go through
 * {@code AdminActions}' guarded path: nothing an invite does can remove an admin, and a pending invite never counts as
 * one (ADR-048). The activation token carries the admin-issued marker, which is what keeps a self-registration of the
 * same address from replacing the invite (ADR-007 amendment).
 */
@Service
public class AdminInvitations {

    /** The unique indexes an invite inserts into (V2). */
    private static final Set<String> UNIQUE_IDENTIFIER_INDEXES = Set.of(UniqueIdentifierIndexes.USERS_USERNAME,
            UniqueIdentifierIndexes.USERS_EMAIL);

    private final UserAccountRepository accounts;
    private final Tombstones tombstones;
    private final CredentialTokens tokens;
    private final AuditEmitter audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AdminInvitations(UserAccountRepository accounts, Tombstones tombstones, CredentialTokens tokens,
            AuditEmitter audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.accounts = accounts;
        this.tombstones = tombstones;
        this.tokens = tokens;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Invites {@code username} at {@code submittedEmail} as {@code role}, for {@code actorId}, or re-invites the pending
     * invite both already name.
     *
     * @return the account and its activation token, to return once and never log
     * @throws InvalidIdentifierException if the username or the email address is not acceptable as submitted
     * @throws UserExistsException        if a tombstone, or an account other than a pending invite of both, holds the
     *                                    username or the email address
     */
    @PreAuthorize("hasRole('ADMIN')")
    public IssuedToken invite(UUID actorId, String username, String submittedEmail, String role) {
        if (!Identifiers.validUsername(username)) {
            throw new InvalidIdentifierException();
        }
        String email = Identifiers.canonicalEmail(submittedEmail).orElseThrow(InvalidIdentifierException::new);
        Invited invited;
        try {
            invited = transactions.execute(status -> create(username, email, role));
        } catch (DataIntegrityViolationException e) {
            // A concurrent invite or registration of the same identifier committed first: the same 400 as one taken.
            throw UniqueIdentifierIndexes.violated(e, UNIQUE_IDENTIFIER_INDEXES) ? new UserExistsException() : e;
        }
        audit.emit(invited.reissued() ? AuditEvent.ADMIN_USER_REINVITED : AuditEvent.ADMIN_USER_INVITED,
                AdminActionContext.applied(actorId, invited.issued().userId()));
        return invited.issued();
    }

    private Invited create(String username, String email, String role) {
        // Email first, then username: the lock order a registration of the same address takes (ADR-032).
        Optional<UserAccount> byEmail = accounts.findForUpdateByEmail(email);
        Optional<UserAccount> byUsername = accounts.findForUpdateByUsername(username);
        // After the locks, so a delete that held one and committed first is seen by its tombstone (ADR-044).
        if (tombstones.holdsUsername(username) || tombstones.holdsEmail(email)) {
            throw new UserExistsException();
        }
        if (byEmail.isEmpty() && byUsername.isEmpty()) {
            UserAccount account = accounts.saveAndFlush(UserAccount.invitation(username, email, role,
                    clock.instant().truncatedTo(ChronoUnit.MICROS)));
            return new Invited(issue(account), false);
        }
        UserAccount account = byEmail
                .filter(named -> byUsername.filter(other -> other.getId().equals(named.getId())).isPresent())
                .filter(this::isReinvitable)
                .orElseThrow(UserExistsException::new);
        account.reinvite(role);
        return new Invited(issue(account), true);
    }

    /** A pending invite an administrator may issue again: enabled, never activated, and marked invited by its token. */
    private boolean isReinvitable(UserAccount account) {
        return account.isPending() && account.isEnabled() && tokens.invited(account.getId());
    }

    /** Mints the account's admin-issued activation token, cancelling any it still had outstanding (ADR-007). */
    private IssuedToken issue(UserAccount account) {
        return new IssuedToken(account.getId(), tokens.issueForAdmin(account.getId(), CredentialTokenType.ACTIVATION));
    }

    /** What an invite did: the token it issued, and whether it re-invited an existing pending invite. */
    private record Invited(IssuedToken issued, boolean reissued) {
    }

    /** The username or the email address is not acceptable as submitted: 400 {@code VALIDATION_FAILED}. */
    public static final class InvalidIdentifierException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        InvalidIdentifierException() {
            super("The username or email address is not acceptable");
        }
    }

    /** An account or a tombstone holds the username or the email address: 400 {@code USER_EXISTS} (R-ADM-012). */
    public static final class UserExistsException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        UserExistsException() {
            super("A user with that username or email address already exists");
        }
    }
}
