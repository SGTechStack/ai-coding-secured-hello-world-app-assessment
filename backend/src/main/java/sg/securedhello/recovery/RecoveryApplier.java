package sg.securedhello.recovery;

import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The write step of a confirmed recovery run (ADR-072; ADR-073): every planned account, in one transaction, so a
 * rejected password or any failure rolls every account back.
 *
 * <p>It bypasses {@code AdminActionGuard} on purpose, the one caller outside the guarded service that may remove a
 * factor (ArchUnit): {@code both} must be able to take the system to zero enrolled admins, which is what break-glass
 * means. The password goes through {@link PasswordService} with no exception at the call site, as a forced-change
 * credential (ADR-046); the batch form invalidates and mints nothing.
 */
final class RecoveryApplier {

    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final TotpFactorRemoval factors;
    private final SessionTerminationService sessions;
    private final TransactionTemplate transactions;

    RecoveryApplier(UserAccountRepository accounts, PasswordService passwords, TotpFactorRemoval factors,
            SessionTerminationService sessions, TransactionTemplate transactions) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.factors = factors;
        this.sessions = sessions;
        this.transactions = transactions;
    }

    /**
     * Applies {@code plan}: for the {@code password} scope, clears the lockout and issues {@code password} as a
     * forced-change credential, or invalidates the password when it is {@code null}; for {@code totp}, removes the
     * factor; and ends every session of each account after commit.
     */
    void apply(RecoveryPlan plan, @Nullable String password) {
        transactions.executeWithoutResult(status -> {
            for (RecoveryPlan.Account target : plan.accounts()) {
                UserAccount account = accounts.findForUpdateById(target.id()).orElseThrow();
                if (plan.scope().password()) {
                    // Before the password write: its reset-token cleanup flushes and clears the persistence context.
                    account.setLockoutState(PasswordLockoutState.CLEAR);
                    if (password != null) {
                        passwords.issueForcedChangeCredential(target.id(), password);
                    } else {
                        passwords.invalidate(target.id());
                    }
                }
                if (plan.scope().totp()) {
                    factors.remove(target.id());
                }
                sessions.endAll(target.username());
            }
        });
    }
}
