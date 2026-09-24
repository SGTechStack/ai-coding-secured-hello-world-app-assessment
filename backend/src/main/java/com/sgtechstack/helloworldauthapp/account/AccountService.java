package com.sgtechstack.helloworldauthapp.account;

import com.sgtechstack.helloworldauthapp.admin.LastAdminGuard;
import com.sgtechstack.helloworldauthapp.audit.AuditAction;
import com.sgtechstack.helloworldauthapp.audit.AuditService;
import com.sgtechstack.helloworldauthapp.auth.SessionRevoker;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.logging.UserPseudonym;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetToken;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.privacy.PrivacyProperties;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a user can do with their own personal data: read all of it, and have it
 * erased.
 *
 * <h2>Why self-service and not an admin request queue</h2>
 *
 * Before this, deletion was admin-only and export did not exist. Both rights
 * were therefore exercisable only by asking somebody — which is a process, not a
 * feature, and this repository has no process. Worse, routing an erasure request
 * through an administrator means the request itself creates a record of who asked
 * to be forgotten, held by the party they are asking.
 *
 * <p>Self-service also removes the question of whether the requester is who they
 * claim to be: they are holding the session and re-proving the password, which is
 * stronger identity evidence than any email-based verification an admin workflow
 * would use.
 *
 * <h2>Erasure is a real delete</h2>
 *
 * The row is removed, not flagged. A soft delete was considered — it is the safer
 * choice for the admin-initiated path, where an accidental deletion is a genuine
 * risk — and rejected here, because "erasure" that leaves the data in place with
 * a boolean beside it is the thing the right exists to prevent. The safety that a
 * soft delete would have provided comes instead from the re-proved password.
 *
 * <p>The audit record survives the account, deliberately: it holds a pseudonymous
 * reference and the fact that an erasure happened, which is what makes the
 * erasure demonstrable later. It cannot be used to reconstruct who was erased
 * without the pseudonym key, so it is not a copy of the data that was removed.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SessionRevoker sessionRevoker;
    private final StepUpAuthenticator stepUpAuthenticator;
    private final LastAdminGuard lastAdminGuard;
    private final AuditService auditService;
    private final UserPseudonym pseudonym;
    private final PrivacyProperties privacyProperties;

    public AccountService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SessionRevoker sessionRevoker,
            StepUpAuthenticator stepUpAuthenticator,
            LastAdminGuard lastAdminGuard,
            AuditService auditService,
            UserPseudonym pseudonym,
            PrivacyProperties privacyProperties
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRevoker = sessionRevoker;
        this.stepUpAuthenticator = stepUpAuthenticator;
        this.lastAdminGuard = lastAdminGuard;
        this.auditService = auditService;
        this.pseudonym = pseudonym;
        this.privacyProperties = privacyProperties;
    }

    /**
     * Everything held about {@code accountId}, for that account's own owner.
     *
     * <p>Audited. An export is a bulk read of personal data, and recording it is
     * what lets "somebody exported this account" be distinguished later from
     * "somebody with the session read a few pages". That the subject authorised
     * it does not make the read uninteresting.
     */
    @Transactional
    public AccountExportResponse export(UUID accountId) {
        User account = requireAccount(accountId);

        List<AccountExportResponse.ResetRequestRecord> resetHistory =
                tokenRepository.findAllByUserOrderByExpiresAtAsc(account).stream()
                        .map(AccountService::toResetRecord)
                        .toList();

        auditService.record(AuditAction.SELF_EXPORT, accountId, account.getUsername(),
                accountId, account.getUsername(), "resetRequests=" + resetHistory.size());

        log.info("Account data exported userRef={} resetRequests={}",
                pseudonym.of(account.getUsername()), resetHistory.size());

        return new AccountExportResponse(
                Instant.now(),
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getRole(),
                account.isEnabled(),
                account.getCreatedAt(),
                account.getFailedLoginAttempts(),
                account.getLockedUntil(),
                account.getLastFailedLoginAt(),
                resetHistory,
                privacyProperties
        );
    }

    /**
     * Erases {@code accountId} and every reset token belonging to it.
     *
     * <p>The last-admin guard applies here as much as it does to the admin path.
     * Without it, the sole administrator could erase their own account and leave
     * the system with nobody able to administer it — and unlike the admin path,
     * the "not yourself" check offers no protection at all, because targeting
     * yourself is the entire point of this endpoint.
     *
     * @param confirmationPassword the account's current password, re-entered
     */
    @Transactional
    public void erase(UUID accountId, String confirmationPassword) {
        stepUpAuthenticator.requirePassword(accountId, confirmationPassword);

        User account = requireAccount(accountId);
        lastAdminGuard.requireNotLastEnabledAdmin(account, "erase");

        String username = account.getUsername();

        tokenRepository.deleteAllByUser(account);
        userRepository.delete(account);

        int revokedSessions = sessionRevoker.revokeAllSessionsFor(accountId);

        auditService.record(AuditAction.SELF_ERASURE, accountId, username, accountId, username,
                "revokedSessions=" + revokedSessions);

        log.info("Account erased by its owner userRef={} revokedSessions={}",
                pseudonym.of(username), revokedSessions);
    }

    private User requireAccount(UUID accountId) {
        return userRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Your account no longer exists"));
    }

    private static AccountExportResponse.ResetRequestRecord toResetRecord(PasswordResetToken token) {
        return new AccountExportResponse.ResetRequestRecord(token.getExpiresAt(), token.getUsedAt());
    }
}
