package sg.securedhello.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import sg.securedhello.admin.AdminActionGuard.Mutation;
import sg.securedhello.admin.AuthenticableAdmins.Standing;
import sg.securedhello.audit.AdminActionContext;
import sg.securedhello.audit.AdminRefusalReason;
import sg.securedhello.audit.AdminUnlockContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.UnlockReason;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.mfa.TotpUserDetailsRepository;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.DeviceLockState;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.TrustedDeviceRepository;
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
    private final Tombstones tombstones;
    private final TotpFactorRemoval factorRemoval;
    private final CredentialTokens tokens;
    private final TotpUserDetailsRepository factors;
    private final TrustedDeviceRepository devices;
    private final AdminUserViews views;

    AdminActions(AuthenticableAdmins admins, UserAccountRepository accounts, PasswordService passwords,
            SessionTerminationService sessions, AuditEmitter audit, Tombstones tombstones,
            TotpFactorRemoval factorRemoval, CredentialTokens tokens, TotpUserDetailsRepository factors,
            TrustedDeviceRepository devices, AdminUserViews views) {
        this.admins = admins;
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
        this.tombstones = tombstones;
        this.factorRemoval = factorRemoval;
        this.tokens = tokens;
        this.factors = factors;
        this.devices = devices;
        this.views = views;
    }

    /**
     * Enables or disables {@code subjectId} for {@code actorId} (PRD Story 9). A disable ends the subject's sessions
     * after commit (ADR-037), cancels its pending activation and reset tokens (ADR-007) and revokes its trusted
     * devices (ADR-075). A re-enable, from
     * disabled, issues a forced-change credential (ADR-046). Setting the state an account already has changes nothing
     * but is still audited.
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
                tokens.cancelAll(account.getId());
                devices.revokeAll(account.getId());
            } else if (!wasEnabled) {
                passwords.issueForcedChangeCredential(account.getId());
            }
            afterCommit(() -> audit.emit(enabled ? AuditEvent.ADMIN_USER_ENABLED : AuditEvent.ADMIN_USER_DISABLED,
                    AdminActionContext.applied(actorId, subjectId)));
        });
    }

    /**
     * Changes {@code subjectId}'s role to {@code role}, {@code USER} or {@code ADMIN}, for {@code actorId} (PRD Story
     * 10). A change ends the subject's sessions after commit, so no session keeps the old authorities (ADR-037).
     * Setting the role an account already has changes nothing but is still audited, as {@link #setEnabled} is.
     *
     * @return the account as it now is, or empty if no account has {@code subjectId}
     * @throws AdminActionRefusedException if the guard refuses it; nothing changes
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Optional<AdminUserView> setRole(UUID actorId, UUID subjectId, Role role) {
        boolean promote = role == Role.ADMIN;
        return guarded(promote ? Mutation.PROMOTE : Mutation.DEMOTE, actorId, subjectId, account -> {
            if (!account.getRole().equals(role.name())) {
                account.setRole(role.name());
                sessions.endAll(account.getUsername());
            }
            afterCommit(() -> audit.emit(promote ? AuditEvent.ADMIN_USER_PROMOTED : AuditEvent.ADMIN_USER_DEMOTED,
                    AdminActionContext.applied(actorId, subjectId)));
        });
    }

    /**
     * Deletes {@code subjectId} for {@code actorId}, leaving its tombstone in the same transaction (PRD Story 11;
     * ADR-044), and ends its sessions after commit (ADR-037). Its trusted devices go with its row, by cascade.
     *
     * @return whether an account had {@code subjectId}
     * @throws AdminActionRefusedException if the guard refuses it; nothing changes
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public boolean delete(UUID actorId, UUID subjectId) {
        return guarded(Mutation.DELETE, actorId, subjectId, account -> {
            tombstones.deleteLeavingTombstone(account, actorId);
            sessions.endAll(account.getUsername());
            afterCommit(() -> audit.emit(AuditEvent.ADMIN_USER_DELETED,
                    AdminActionContext.applied(actorId, subjectId)));
        }).isPresent();
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
     * Unlocks {@code subjectId} for {@code actorId} (R-AUTH-002): clears its password lockout in every lane, the
     * untrusted lane's windowed counter and lock and each trusted device's (ADR-075), and its factor's tier-1 lock.
     * Never the NIST cap's password disable nor the factor's tier-2 disable, which only rebinding clears (REJ-072;
     * ADR-013; ADR-027). An administrator may not unlock themselves (REJ-050).
     * Unlocking an account with no lock changes nothing but is still audited, with {@code reason} (REJ-028).
     *
     * @return the account as it now is, or empty if no account has {@code subjectId}
     * @throws AdminActionRefusedException if the guard refuses it; nothing changes
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Optional<AdminUserView> unlock(UUID actorId, UUID subjectId, UnlockReason reason) {
        return guarded(Mutation.UNLOCK, actorId, subjectId, account -> {
            account.setLockoutState(account.getLockoutState().unlocked());
            // Under the account's row lock, which every device-row write takes (ADR-075).
            devices.findByUserIdOrderByCreatedAtDesc(subjectId)
                    .forEach(device -> device.setLockState(DeviceLockState.CLEAR));
            // Its row is already locked: the guard's lock set takes the subject's factor row too (ADR-048).
            factors.findById(subjectId).ifPresent(factor -> factor.unlockTier1());
            afterCommit(() -> audit.emit(AuditEvent.ADMIN_USER_UNLOCKED,
                    new AdminUnlockContext(actorId, subjectId, reason)));
        });
    }

    /**
     * Issues {@code actorId} a password-reset token for {@code subjectId}, their own account included (ADR-006): it
     * replaces the subject's pending reset tokens, ends the subject's sessions after commit (ADR-037) and revokes its
     * trusted devices (ADR-075). It clears nothing else, the lock included (REJ-016; R-LCK-010); the user's redemption at the ordinary confirm does. The token
     * is marked admin-issued, so a self-service request leaves it in place (ADR-007).
     *
     * @return the plaintext token, to return once and never log, or empty if no account has {@code subjectId}
     * @throws NotResettableException if the account is not activated or not enabled; nothing changes (R-STD-024)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Optional<IssuedToken> issuePasswordReset(UUID actorId, UUID subjectId) {
        return guardedThen(Mutation.PASSWORD_RESET, actorId, subjectId, account -> {
            if (account.isPending() || !account.isEnabled()) {
                throw new NotResettableException();
            }
            String token = tokens.issueForAdmin(subjectId, CredentialTokenType.PASSWORD_RESET);
            sessions.endAll(account.getUsername());
            devices.revokeAll(subjectId);
            afterCommit(() -> audit.emit(AuditEvent.ADMIN_RESET_ISSUED,
                    AdminActionContext.applied(actorId, subjectId)));
            return new IssuedToken(subjectId, token);
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
     * So is a lock set that other changes held past the database lock timeout: nothing has been written yet, and the
     * caller is told to retry rather than shown a 500.
     */
    private Optional<AdminUserView> guarded(Mutation mutation, UUID actorId, UUID subjectId,
            Consumer<UserAccount> change) {
        return guardedThen(mutation, actorId, subjectId, account -> {
            change.accept(account);
            return views.of(account);
        });
    }

    /** The guarded path itself, for a mutation that answers with something other than the account's view. */
    private <T> Optional<T> guardedThen(Mutation mutation, UUID actorId, UUID subjectId,
            Function<UserAccount, T> change) {
        List<Standing> lockSet = lockSet(actorId, subjectId);
        if (lockSet.stream().map(Standing::id).noneMatch(subjectId::equals)) {
            return Optional.empty();
        }
        AdminActionGuard.decide(mutation, actorId, subjectId, lockSet).ifPresent(reason -> {
            throw refuse(actorId, subjectId, reason);
        });
        UserAccount account = accounts.findById(subjectId).orElseThrow();
        return Optional.of(change.apply(account));
    }

    /**
     * Takes the guard's lock set, or refuses as {@link AdminRefusalReason#LOCK_TIMEOUT} if it timed out. H2 reports a
     * lock wait past {@code LOCK_TIMEOUT} as a query timeout and a deadlock as a pessimistic-locking failure; a retry
     * can succeed after either. Any other database fault is not contention and stays a 500.
     */
    private List<Standing> lockSet(UUID actorId, UUID subjectId) {
        try {
            return admins.lockForChange(subjectId);
        } catch (QueryTimeoutException | PessimisticLockingFailureException contended) {
            throw refuse(actorId, subjectId, AdminRefusalReason.LOCK_TIMEOUT);
        }
    }

    /** Writes the refusal row at once and returns the exception that rolls the transaction back (row 34). */
    private AdminActionRefusedException refuse(UUID actorId, UUID subjectId, AdminRefusalReason reason) {
        audit.emit(AuditEvent.ADMIN_ACTION_REFUSED, AdminActionContext.refused(actorId, subjectId, reason));
        return new AdminActionRefusedException(reason);
    }
}
