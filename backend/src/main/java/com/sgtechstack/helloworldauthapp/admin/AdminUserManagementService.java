package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.audit.AuditAction;
import com.sgtechstack.helloworldauthapp.audit.AuditService;
import com.sgtechstack.helloworldauthapp.auth.SessionRevoker;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.logging.LogSafe;
import com.sgtechstack.helloworldauthapp.logging.UserPseudonym;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin actions against another user's account: enable/disable, role
 * change, delete, and the purpose-stated email lookup.
 *
 * <p>Role enforcement (that the caller is actually an admin) happens at the
 * HTTP layer, in {@code SecurityConfig}; this service enforces the business
 * rules that authorization cannot express.
 *
 * <h2>The four guards, and why each is separate</h2>
 *
 * <ol>
 *   <li><strong>Not yourself</strong> ({@link #requireNotSelf}) — an admin can
 *       never apply any of these to their own account, so they cannot
 *       accidentally remove their own access.</li>
 *   <li><strong>Not the last enabled admin</strong> ({@link LastAdminGuard}) —
 *       "not yourself" is sufficient protection only while there is one admin.
 *       With two, each can eliminate the other, so the self-check passes at
 *       every step of a walk down to zero administrators. See that class.</li>
 *   <li><strong>Re-proved password on delete</strong> ({@link
 *       StepUpAuthenticator}) — a session proves somebody authenticated hours
 *       ago, not that this request came from them. For the one action with no
 *       undo, that is not enough.</li>
 *   <li><strong>Sanctioned role mutation</strong> ({@link RoleMutationGuard}) —
 *       a single named checkpoint that the only legitimate role-change path
 *       routes through.</li>
 * </ol>
 *
 * <h2>Session revocation</h2>
 *
 * Every mutation that narrows what the target account can do also revokes that
 * account's live sessions. Writing the row is not enough on its own:
 * authorities are cached in the session established at login, so a suspended
 * user would otherwise keep working and a demoted admin would keep admin
 * authorities for long enough to undo their own demotion.
 *
 * <h2>Audit</h2>
 *
 * Each mutation writes an {@code AuditEvent} inside the same transaction as the
 * change. The log line that used to be the only record is still emitted, but it
 * is now a mirror of the row rather than the record itself — and it carries a
 * pseudonymous reference instead of the username, so the log stops accumulating
 * a permanent second copy of who-did-what-to-whom.
 */
@Service
public class AdminUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserManagementService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SessionRevoker sessionRevoker;
    private final RoleMutationGuard roleMutationGuard;
    private final LastAdminGuard lastAdminGuard;
    private final StepUpAuthenticator stepUpAuthenticator;
    private final AuditService auditService;
    private final UserPseudonym pseudonym;

    public AdminUserManagementService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SessionRevoker sessionRevoker,
            RoleMutationGuard roleMutationGuard,
            LastAdminGuard lastAdminGuard,
            StepUpAuthenticator stepUpAuthenticator,
            AuditService auditService,
            UserPseudonym pseudonym
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRevoker = sessionRevoker;
        this.roleMutationGuard = roleMutationGuard;
        this.lastAdminGuard = lastAdminGuard;
        this.stepUpAuthenticator = stepUpAuthenticator;
        this.auditService = auditService;
        this.pseudonym = pseudonym;
    }

    @Transactional
    public User setEnabled(UUID actingAdminId, UUID targetUserId, boolean enabled) {
        requireNotSelf(actingAdminId, targetUserId, "disable or enable");

        User target = requireUser(targetUserId);

        if (!enabled) {
            // Only disabling removes capability, so only disabling can strand
            // the system without an administrator.
            lastAdminGuard.requireNotLastEnabledAdmin(target, "disable");
        }

        target.setEnabled(enabled);
        userRepository.save(target);

        // Only disabling needs revocation. Enabling widens access, and a
        // disabled account shouldn't have had a live session to begin with.
        int revokedSessions = enabled ? 0 : sessionRevoker.revokeAllSessionsFor(targetUserId);

        auditService.record(AuditAction.SET_ENABLED, actingAdminId, actingUsername(actingAdminId),
                targetUserId, target.getUsername(),
                "enabled=" + enabled + " revokedSessions=" + revokedSessions);

        log.info("Admin action actorRef={} targetRef={} action={} enabled={} revokedSessions={}",
                pseudonym.of(actingUsername(actingAdminId)), pseudonym.of(target.getUsername()),
                "SET_ENABLED", enabled, revokedSessions);

        return target;
    }

    @Transactional
    public User changeRole(UUID actingAdminId, UUID targetUserId, Role newRole) {
        requireNotSelf(actingAdminId, targetUserId, "change the role of");

        User target = requireUser(targetUserId);
        Role previousRole = target.getRole();

        if (newRole != Role.ADMIN) {
            lastAdminGuard.requireNotLastEnabledAdmin(target, "demote");
        }

        roleMutationGuard.recordSanctionedMutation(actingAdminId, targetUserId, previousRole, newRole);
        target.setRole(newRole);
        userRepository.save(target);

        // Revoke in both directions, not just on demotion. A demotion has to
        // take effect immediately or it can be undone by the very session it
        // failed to cut; a promotion has to, or the newly-promoted admin sits
        // there with stale USER authorities wondering why nothing changed.
        // One rule is also easier to reason about than two.
        int revokedSessions = sessionRevoker.revokeAllSessionsFor(targetUserId);

        auditService.record(AuditAction.CHANGE_ROLE, actingAdminId, actingUsername(actingAdminId),
                targetUserId, target.getUsername(),
                previousRole + " -> " + newRole + " revokedSessions=" + revokedSessions);

        log.info("Admin action actorRef={} targetRef={} action={} previousRole={} newRole={} revokedSessions={}",
                pseudonym.of(actingUsername(actingAdminId)), pseudonym.of(target.getUsername()),
                "CHANGE_ROLE", previousRole, newRole, revokedSessions);

        return target;
    }

    /**
     * Deletes an account. Irreversible, and therefore the one action that
     * requires the acting admin to re-enter their password.
     *
     * @param confirmationPassword the acting admin's current password, from the
     *                             {@code X-Confirm-Password} header
     */
    @Transactional
    public void deleteUser(UUID actingAdminId, UUID targetUserId, String confirmationPassword) {
        requireNotSelf(actingAdminId, targetUserId, "delete");
        stepUpAuthenticator.requirePassword(actingAdminId, confirmationPassword);

        User target = requireUser(targetUserId);
        lastAdminGuard.requireNotLastEnabledAdmin(target, "delete");

        String targetUsername = target.getUsername();

        // No cascade is configured at the entity level between User and
        // PasswordResetToken (deliberately: the user package doesn't
        // depend on the passwordreset package), so any outstanding tokens
        // for this user must be cleared explicitly before the delete, or
        // it fails on the foreign key.
        tokenRepository.deleteAllByUser(target);
        userRepository.delete(target);

        // A deleted user's session would otherwise stay authenticated against
        // a principal that no longer exists.
        int revokedSessions = sessionRevoker.revokeAllSessionsFor(targetUserId);

        // The audit row has no foreign key to users precisely so that it can be
        // written for a row that no longer exists. This is the case that
        // justified the design.
        auditService.record(AuditAction.DELETE_USER, actingAdminId, actingUsername(actingAdminId),
                targetUserId, targetUsername,
                "revokedSessions=" + revokedSessions);

        log.info("Admin action actorRef={} targetRef={} action={} revokedSessions={}",
                pseudonym.of(actingUsername(actingAdminId)), pseudonym.of(targetUsername),
                "DELETE", revokedSessions);
    }

    /**
     * Reveals one account's email address, for a stated purpose, with an audit
     * record.
     *
     * <p>This is what replaced returning every address in the user listing. The
     * purpose is mandatory and is stored: purpose limitation that leaves no
     * trace is an assertion, not a control, and the point of the record is that
     * a bulk harvest through this endpoint is visible afterwards as a run of
     * lookups with identical or absent justification.
     *
     * @throws PurposeRequiredException if no purpose was supplied
     */
    @Transactional
    public UserEmailResponse readEmail(UUID actingAdminId, UUID targetUserId, String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new PurposeRequiredException("A purpose is required to read a user's email address");
        }

        User target = requireUser(targetUserId);

        auditService.record(AuditAction.READ_USER_EMAIL, actingAdminId, actingUsername(actingAdminId),
                targetUserId, target.getUsername(), "purpose=" + purpose);

        log.info("Admin action actorRef={} targetRef={} action={} purpose={}",
                pseudonym.of(actingUsername(actingAdminId)), pseudonym.of(target.getUsername()),
                "READ_USER_EMAIL", LogSafe.value(purpose));

        return new UserEmailResponse(target.getId(), target.getUsername(), target.getEmail(), purpose);
    }

    private void requireNotSelf(UUID actingAdminId, UUID targetUserId, String actionDescription) {
        if (actingAdminId.equals(targetUserId)) {
            throw new SelfActionNotAllowedException("An admin cannot " + actionDescription + " their own account");
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user with that id"));
    }

    /**
     * The acting admin's username, used only to derive the pseudonymous
     * reference for the audit record and the log line.
     *
     * <p>Tolerates the account being absent rather than throwing: the acting
     * admin is authenticated, so this should always resolve, but an audit write
     * must never be the thing that fails an otherwise valid mutation. Losing
     * the actor reference degrades the record; throwing here would discard both
     * the record and the action.
     */
    private String actingUsername(UUID actingAdminId) {
        return userRepository.findById(actingAdminId).map(User::getUsername).orElse(null);
    }
}
