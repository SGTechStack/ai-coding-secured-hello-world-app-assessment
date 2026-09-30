package sg.securedhello.admin;

import java.io.Serial;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.core.NestedExceptionUtils;
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
 * <p>An invite has no subject yet, so neither guard check can apply, and it does not go through
 * {@code AdminActions}' guarded path: nothing an invite does can remove an admin, and a pending invite never counts as
 * one (ADR-048). The activation token carries the admin-issued marker, which is what keeps a self-registration of the
 * same address from replacing the invite (ADR-007 amendment).
 */
@Service
public class AdminInvitations {

    /** The unique indexes an invite inserts into (V2). */
    private static final List<String> UNIQUE_IDENTIFIER_INDEXES = List.of("UX_USERS_USERNAME", "UX_USERS_EMAIL");

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
     * Invites {@code username} at {@code submittedEmail} as {@code role}, for {@code actorId}.
     *
     * @return the new account and its activation token, to return once and never log
     * @throws InvalidIdentifierException if the username or the email address is not acceptable as submitted
     * @throws UserExistsException        if an account or a tombstone holds the username or the email address
     */
    @PreAuthorize("hasRole('ADMIN')")
    public IssuedToken invite(UUID actorId, String username, String submittedEmail, String role) {
        if (!Identifiers.validUsername(username)) {
            throw new InvalidIdentifierException();
        }
        String email = Identifiers.canonicalEmail(submittedEmail).orElseThrow(InvalidIdentifierException::new);
        IssuedToken issued;
        try {
            issued = transactions.execute(status -> create(username, email, role));
        } catch (DataIntegrityViolationException e) {
            throw identifierRace(e);
        }
        audit.emit(AuditEvent.ADMIN_USER_INVITED, AdminActionContext.applied(actorId, issued.userId()));
        return issued;
    }

    private IssuedToken create(String username, String email, String role) {
        if (accounts.findByUsername(username).isPresent() || accounts.findByEmail(email).isPresent()
                || tombstones.holdsUsername(username) || tombstones.holdsEmail(email)) {
            throw new UserExistsException();
        }
        UserAccount account = accounts.saveAndFlush(UserAccount.invitation(username, email, role,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return new IssuedToken(account.getId(), tokens.issueForAdmin(account.getId(), CredentialTokenType.ACTIVATION));
    }

    /**
     * A concurrent invite or registration of the same identifier committed first, so this insert hit a unique index:
     * the same 400 as an identifier found taken. Any other integrity failure stays a failure.
     */
    private static RuntimeException identifierRace(DataIntegrityViolationException e) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                .toUpperCase(Locale.ROOT);
        return UNIQUE_IDENTIFIER_INDEXES.stream().anyMatch(cause::contains) ? new UserExistsException() : e;
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
