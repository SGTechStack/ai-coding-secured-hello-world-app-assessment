package sg.securedhello.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import sg.securedhello.admin.AdminActionGuard.Mutation;
import sg.securedhello.admin.AuthenticableAdmins.Standing;
import sg.securedhello.audit.AdminActionContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The admin mutations (ADR-048). Every one goes through {@link #guarded}, the single method that takes the uniform
 * lock set and calls {@link AdminActionGuard}, in one transaction, before anything changes. Lock order is
 * {@code users}, then {@code totp_user_details}, then the session rows, which {@link SessionTerminationService} ends
 * after commit. The factor is enforced by the request matchers only; {@code @PreAuthorize} repeats the role check and
 * nothing else (ADR-026; ADR-043).
 */
@Service
public class AdminActions {

    private final AuthenticableAdmins admins;
    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final TotpFactorRemoval factorRemoval;

    AdminActions(AuthenticableAdmins admins, UserAccountRepository accounts, PasswordService passwords,
            SessionTerminationService sessions, AuditEmitter audit, TotpFactorRemoval factorRemoval) {
        this.admins = admins;
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
        this.factorRemoval = factorRemoval;
    }

    /**
     * Enables or disables {@code subjectId} for {@code actorId} (PRD Story 9). A disable ends the subject's sessions
     * after commit (ADR-037). A re-enable, from disabled, issues a forced-change credential (ADR-046). Setting the
     * state an account already has changes nothing but is still audited.
     *
     * @return the account as it now is, or empty if no account has {@code subjectId}
     * @throws AdminActionRefusedException if the guard refuses it; nothing changes
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Optional<AdminUserView> setEnabled(UUID actorId, UUID subjectId, boolean enabled) {
        return guarded(enabled ? Mutation.ENABLE : Mutation.DISABLE, actorId, subjectId, account -> {
            boolean wasEnabled = account.isEnabled();
            account.setEnabled(enabled);
            if (!enabled) {
                sessions.endAll(account.getUsername());
            } else if (!wasEnabled) {
                passwords.issueForcedChangeCredential(account.getId());
            }
            afterCommit(() -> audit.emit(enabled ? AuditEvent.ADMIN_USER_ENABLED : AuditEvent.ADMIN_USER_DISABLED,
                    AdminActionContext.applied(actorId, subjectId)));
        });
    }

    /**
     * Resets {@code subjectId}'s TOTP factor for {@code actorId} (ADR-024; ADR-049): deletes its confirmed and pending
     * rows, which clears a tier-2 disable too, and ends its sessions after commit (ADR-037). The subject re-enrols at
     * their next sign-in. Check 1 applies; the two-admin count does not, since the subject restores it alone. An
     * account with no factor loses nothing but the reset is still audited.
     *
     * @return the account, or empty if no account has {@code subjectId}
     * @throws AdminActionRefusedException if the guard refuses it; nothing changes
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Optional<AdminUserView> resetFactor(UUID actorId, UUID subjectId) {
        return guarded(Mutation.FACTOR_RESET, actorId, subjectId, account -> {
            factorRemoval.remove(subjectId);
            sessions.endAll(account.getUsername());
            afterCommit(() -> audit.emit(AuditEvent.TOTP_REMOVED, AdminActionContext.applied(actorId, subjectId)));
        });
    }

    /**
     * Runs {@code row} once the transaction commits, so an applied row never records a change that rolled back
     * (R-AUD-009). A refusal's row is written at once instead: the refusal happened, and its rollback is the point.
     */
    private static void afterCommit(Runnable row) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                row.run();
            }
        });
    }

    /**
     * The one guarded path: locks every admin row and the subject's, then their factor rows, asks the guard, and only
     * then applies {@code change} to the subject. A refusal is audited and thrown, which rolls the transaction back.
     */
    private Optional<AdminUserView> guarded(Mutation mutation, UUID actorId, UUID subjectId,
            Consumer<UserAccount> change) {
        List<Standing> lockSet = admins.lockForChange(subjectId);
        if (lockSet.stream().map(Standing::id).noneMatch(subjectId::equals)) {
            return Optional.empty();
        }
        AdminActionGuard.decide(mutation, actorId, subjectId, lockSet).ifPresent(reason -> {
            audit.emit(AuditEvent.ADMIN_ACTION_REFUSED, AdminActionContext.refused(actorId, subjectId, reason));
            throw new AdminActionRefusedException(reason);
        });
        UserAccount account = accounts.findById(subjectId).orElseThrow();
        change.accept(account);
        return Optional.of(AdminUserView.of(account));
    }
}
