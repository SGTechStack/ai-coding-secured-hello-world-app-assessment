package com.example.auth.admin;

import java.util.List;
import java.util.UUID;

import com.example.auth.audit.AuditService;
import com.example.auth.exception.SelfActionException;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.passwordreset.PasswordResetTokenRepository;
import com.example.auth.session.SessionInvalidator;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import com.example.auth.user.UserResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin account-management operations. All authorization is enforced server-side:
 * the SecurityConfig filter chain requires ROLE_ADMIN for /api/admin/**, and the
 * acting admin's identity is always taken from the authenticated principal
 * (passed in as actingUserId), never from a request field. The {targetId} only
 * selects the account being acted upon — it can never designate "self" or grant
 * privilege (Story 49).
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionInvalidator sessionInvalidator;
    private final AuditService audit;

    public AdminUserService(UserRepository users,
                            PasswordResetTokenRepository resetTokens,
                            SessionInvalidator sessionInvalidator,
                            AuditService audit) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessionInvalidator = sessionInvalidator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse setEnabled(UUID actingUserId, UUID targetId, boolean enabled) {
        User target = requireDifferentTarget(actingUserId, targetId,
                "An admin cannot change their own account status.");

        if (enabled) {
            target.enable();
        } else {
            target.disable();
            // Story 43: disabling must terminate the user's existing sessions.
            sessionInvalidator.invalidateAllSessions(target.getUsername());
        }
        audit.adminUserAction(actingUserId.toString(), target.getUsername(), enabled ? "enable" : "disable");
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse changeRole(UUID actingUserId, UUID targetId, Role role) {
        User target = requireDifferentTarget(actingUserId, targetId,
                "An admin cannot change their own role.");

        target.changeRole(role);
        // Story 44: any role change terminates existing sessions so stale authorities
        // (in either direction) cannot remain active.
        sessionInvalidator.invalidateAllSessions(target.getUsername());
        audit.adminUserAction(actingUserId.toString(), target.getUsername(), "role_change:" + role.name());
        return UserResponse.from(target);
    }

    @Transactional
    public void deleteUser(UUID actingUserId, UUID targetId) {
        User target = requireDifferentTarget(actingUserId, targetId,
                "An admin cannot delete their own account.");

        // Terminate sessions first; a deleted user must not retain a live session.
        sessionInvalidator.invalidateAllSessions(target.getUsername());
        // Remove dependent reset tokens (FK) before deleting the user row.
        resetTokens.deleteByUser(target);
        users.delete(target);
        audit.adminUserAction(actingUserId.toString(), target.getUsername(), "delete");
    }

    /**
     * Loads the target, rejecting the action if it targets the acting admin.
     * The self-check compares the authenticated principal's id with the target id
     * — a crafted {targetId} equal to the admin's own id still triggers 409, and
     * one belonging to another user cannot masquerade as self.
     */
    private User requireDifferentTarget(UUID actingUserId, UUID targetId, String selfMessage) {
        if (actingUserId.equals(targetId)) {
            throw new SelfActionException(selfMessage);
        }
        return users.findById(targetId).orElseThrow(UserNotFoundException::new);
    }
}
