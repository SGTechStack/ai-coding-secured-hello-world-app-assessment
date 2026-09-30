package sg.securedhello.passwordreset;

import java.io.Serial;
import java.util.Optional;

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
import sg.securedhello.security.ratelimit.AuthRateLimiter;
import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;
import sg.securedhello.security.ratelimit.RateLimit;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * A password-reset request by email address (PRD Story 6). The caller answers the same 202 whatever the address's
 * state, and every state writes the same audit row, which names no account (REJ-002):
 * <ol>
 *   <li>the address is canonicalised (ADR-045); one that cannot be an address is refused;</li>
 *   <li>its per-identifier budget is spent, registered or not, before any lookup (Std §5:452);</li>
 *   <li>only an activated, enabled account is issued a {@code PASSWORD_RESET} token, which cancels its earlier pending
 *       ones (ADR-007). A never-activated account gets nothing (R-CRED-012): its route is to register again. Nor
 *       does an account holding a pending admin-issued reset token: that token stays as it is, so a stranger who
 *       knows the address cannot cancel what the administrator handed on (ADR-007 amendment);</li>
 *   <li>the link, on the configured origin (REJ-022), goes to {@link EmailService} after commit. Only the {@code dev}
 *       stub makes it readable; elsewhere nothing is delivered (ADR-057; R-CRED-021).</li>
 * </ol>
 * Issuing a token clears nothing: no lock, no cap (ADR-009; REJ-016). Nothing here authenticates or hashes a password,
 * so the request never meets the {@code AuthenticationManager} (T-CRED-009).
 */
@Service
public class PasswordResetRequests {

    private final UserAccountRepository accounts;
    private final CredentialTokens tokens;
    private final CredentialLinks links;
    private final EmailService email;
    private final AuthRateLimiter limiter;
    private final AuditEmitter audit;
    private final TransactionTemplate transactions;

    PasswordResetRequests(UserAccountRepository accounts, CredentialTokens tokens, CredentialLinks links,
            EmailService email, AuthRateLimiter limiter, AuditEmitter audit,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.links = links;
        this.email = email;
        this.limiter = limiter;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Requests a reset link for {@code submittedEmail}.
     *
     * @throws InvalidEmailException  if the value cannot be an email address
     * @throws ThrottledException     if the address's reset budget is spent
     */
    public void request(String submittedEmail) {
        String canonicalEmail = Identifiers.canonicalEmail(submittedEmail).orElseThrow(InvalidEmailException::new);
        limiter.tryConsume(RateLimit.PASSWORD_RESET_REQUEST_IDENTIFIER, canonicalEmail).ifPresent(refusal -> {
            throw new ThrottledException(refusal);
        });
        audit.emit(AuditEvent.PASSWORD_RESET_REQUESTED, AccountContext.resetRequested());
        Optional<LinkEmail> link = transactions.execute(status -> issue(canonicalEmail));
        link.ifPresent(email::send);
    }

    private Optional<LinkEmail> issue(String canonicalEmail) {
        return accounts.findByEmail(canonicalEmail)
                .filter(PasswordResetRequests::canReset)
                .filter(account -> !tokens.adminIssuedPending(account.getId(), CredentialTokenType.PASSWORD_RESET))
                .map(account -> links.email(CredentialTokenType.PASSWORD_RESET, canonicalEmail,
                        tokens.mint(account.getId(), CredentialTokenType.PASSWORD_RESET)));
    }

    /**
     * Whether a reset link may be issued: the account has a password to replace, and is not disabled, which would
     * cancel the token anyway (ADR-007).
     */
    private static boolean canReset(UserAccount account) {
        return !account.isPending() && account.isEnabled();
    }

    /** The submitted value cannot be an email address: 400 {@code VALIDATION_FAILED}. */
    public static final class InvalidEmailException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        InvalidEmailException() {
            super("The email address is not acceptable");
        }
    }

    /** The address's reset budget is spent: 429 {@code TOO_MANY_REQUESTS}, whether or not it names an account. */
    public static final class ThrottledException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Refusal refusal;

        ThrottledException(Refusal refusal) {
            super("The reset budget for the address is spent");
            this.refusal = refusal;
        }

        public Refusal refusal() {
            return refusal;
        }
    }
}
