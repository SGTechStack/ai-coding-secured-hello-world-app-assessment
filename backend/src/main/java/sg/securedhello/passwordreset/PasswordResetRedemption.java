package sg.securedhello.passwordreset;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.LockoutClearReason;
import sg.securedhello.credential.CredentialTokenInvalidException;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Redeems a {@code PASSWORD_RESET} token, whether the user requested it or an administrator issued it (ADR-006), in
 * one transaction (ADR-007):
 * <ol>
 *   <li>consume the token first, so a strength error can never confirm that a token was valid;</li>
 *   <li>take the account's row lock and clear its password lockout: the counters, the lock and the NIST cap, since a
 *       new password rebinds the authenticator (ADR-009; ADR-013). No TOTP state is touched;</li>
 *   <li>set the password through {@link PasswordService}, which also clears the forced-change flag and
 *       {@code credential_issued_at} and cancels the other pending reset tokens. Its rejection rolls everything back,
 *       and the token stays redeemable;</li>
 *   <li>end every session of the account after commit (ADR-035; ADR-037). None is created: the user signs in next.</li>
 * </ol>
 * The completed row, and the lock-cleared row when there was a lock or a cap to clear, are written after commit. It
 * never goes through the {@code AuthenticationManager}, which would refuse the capped account it must restore
 * (ADR-009; T-CRED-009).
 */
@Service
public class PasswordResetRedemption {

    private final CredentialTokens tokens;
    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final TransactionTemplate transactions;

    PasswordResetRedemption(CredentialTokens tokens, UserAccountRepository accounts, PasswordService passwords,
            SessionTerminationService sessions, AuditEmitter audit, PlatformTransactionManager transactionManager) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Sets {@code password} on the account {@code token} was issued for.
     *
     * @throws CredentialTokenInvalidException if the token does not redeem as a reset token
     * @throws sg.securedhello.password.PasswordRejectedException if the policy refuses the password
     */
    public void redeem(String token, String password) {
        Redeemed redeemed = transactions.execute(status -> {
            UUID accountId = tokens.redeem(CredentialTokenType.PASSWORD_RESET, token)
                    .orElseThrow(() -> tokens.refused(CredentialTokenType.PASSWORD_RESET, token));
            UserAccount account = accounts.findForUpdateById(accountId)
                    .orElseThrow(CredentialTokenInvalidException::accountGone);
            PasswordLockoutState before = account.getLockoutState();
            // Before setPassword: its token cleanup flushes this change and then clears the persistence context.
            account.setLockoutState(PasswordLockoutState.CLEAR);
            passwords.setPassword(accountId, password);
            sessions.endAll(account.getUsername());
            return new Redeemed(accountId, before.lockedUntil() != null || before.passwordDisabled());
        });
        audit.emit(AuditEvent.PASSWORD_RESET_COMPLETED, AccountContext.of(redeemed.accountId()));
        if (redeemed.lockCleared()) {
            audit.emit(AuditEvent.LOCKOUT_CLEARED, AccountContext.lockCleared(redeemed.accountId(),
                    LockoutClearReason.PASSWORD_RESET_COMPLETED));
        }
    }

    /** The account whose password was reset, and whether it had a lock or a cap that the reset cleared. */
    private record Redeemed(UUID accountId, boolean lockCleared) {
    }
}
