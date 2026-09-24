package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.auth.SessionRevoker;
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
 * change, delete. Every method takes the acting admin's id explicitly and
 * rejects the request outright if it matches the target — an admin can
 * never apply any of these to their own account, so they can't
 * accidentally lock themselves out or delete their own access.
 *
 * Role enforcement (that the caller is actually an admin) happens at the
 * HTTP layer, in {@code SecurityConfig}; this service only enforces the
 * self-action guard, which is a business rule, not an authorization rule.
 *
 * {@link #changeRole} additionally routes through {@link RoleMutationGuard}
 * immediately before writing the new role: see that class for why this
 * exists as a named, independently testable checkpoint rather than an
 * inline log line.
 *
 * Every mutation that narrows what the target account can do also revokes
 * that account's live sessions. Writing the row is not enough on its own:
 * authorities are cached in the session established at login, so a suspended
 * user would otherwise keep working and a demoted admin would keep admin
 * authorities for long enough to undo their own demotion.
 */
@Service
public class AdminUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserManagementService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SessionRevoker sessionRevoker;
    private final RoleMutationGuard roleMutationGuard;

    public AdminUserManagementService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SessionRevoker sessionRevoker,
            RoleMutationGuard roleMutationGuard
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRevoker = sessionRevoker;
        this.roleMutationGuard = roleMutationGuard;
    }

    @Transactional
    public User setEnabled(UUID actingAdminId, UUID targetUserId, boolean enabled) {
        requireNotSelf(actingAdminId, targetUserId, "disable or enable");

        User target = requireUser(targetUserId);
        target.setEnabled(enabled);
        userRepository.save(target);

        // Only disabling needs revocation. Enabling widens access, and a
        // disabled account shouldn't have had a live session to begin with.
        int revokedSessions = enabled ? 0 : sessionRevoker.revokeAllSessionsFor(targetUserId);

        log.info("Admin action actorId={} targetUsername={} action={} enabled={} revokedSessions={}",
                actingAdminId, target.getUsername(), "SET_ENABLED", enabled, revokedSessions);

        return target;
    }

    @Transactional
    public User changeRole(UUID actingAdminId, UUID targetUserId, Role newRole) {
        requireNotSelf(actingAdminId, targetUserId, "change the role of");

        User target = requireUser(targetUserId);
        Role previousRole = target.getRole();
        roleMutationGuard.recordSanctionedMutation(actingAdminId, targetUserId, previousRole, newRole);
        target.setRole(newRole);
        userRepository.save(target);

        // Revoke in both directions, not just on demotion. A demotion has to
        // take effect immediately or it can be undone by the very session it
        // failed to cut; a promotion has to, or the newly-promoted admin sits
        // there with stale USER authorities wondering why nothing changed.
        // One rule is also easier to reason about than two.
        int revokedSessions = sessionRevoker.revokeAllSessionsFor(targetUserId);

        log.info("Admin action actorId={} targetUsername={} action={} newRole={} revokedSessions={}",
                actingAdminId, target.getUsername(), "CHANGE_ROLE", newRole, revokedSessions);

        return target;
    }

    @Transactional
    public void deleteUser(UUID actingAdminId, UUID targetUserId) {
        requireNotSelf(actingAdminId, targetUserId, "delete");

        User target = requireUser(targetUserId);
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

        log.info("Admin action actorId={} targetUsername={} action={} revokedSessions={}",
                actingAdminId, targetUsername, "DELETE", revokedSessions);
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
}
