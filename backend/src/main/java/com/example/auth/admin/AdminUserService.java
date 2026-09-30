package com.example.auth.admin;

import com.example.auth.audit.AuditLogger;
import com.example.auth.auth.ApiException;
import com.example.auth.auth.ErrorCode;
import com.example.auth.security.SessionTerminationService;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user management. Guards (each rejection audited):
 * <ul>
 *   <li>no action on your own account ({@code SELF_ACTION});
 *   <li>never disable, demote or delete the last enabled ADMIN ({@code LAST_ADMIN}). The enabled
 *       admins are row-locked while checking, so two admins can't concurrently demote each other
 *       and leave none.
 * </ul>
 * Disabling, re-roling or deleting a user ends their sessions after commit, so the change takes
 * effect immediately rather than at their next login.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionTerminationService sessionTerminationService;
    private final AuditLogger auditLogger;

    public AdminUserService(
            UserRepository userRepository, SessionTerminationService sessionTerminationService, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.sessionTerminationService = sessionTerminationService;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        return userRepository.findAll().stream().map(AdminUserView::from).toList();
    }

    @Transactional
    public AdminUserView setEnabled(Long targetId, boolean enabled, String currentUsername) {
        String change = enabled ? "account_enabled" : "account_disabled";
        User current = currentUser(currentUsername);
        User target = findTarget(targetId);
        requireNotSelf(current, target, change, "Cannot change your own account status");
        if (!enabled) {
            requireNotLastAdmin(current, target, change);
        }
        target.setEnabled(enabled);
        AdminUserView view = AdminUserView.from(userRepository.save(target));
        auditLogger.adminChange(current.getPublicId(), target.getPublicId(), change, null);
        if (!enabled) {
            sessionTerminationService.terminateAllSessions(target.getUsername(), target.getPublicId(), change);
        }
        return view;
    }

    @Transactional
    public AdminUserView setRole(Long targetId, Role role, String currentUsername) {
        User current = currentUser(currentUsername);
        User target = findTarget(targetId);
        requireNotSelf(current, target, "role_changed", "Cannot change your own role");
        if (role != Role.ADMIN) {
            requireNotLastAdmin(current, target, "role_changed");
        }
        target.setRole(role);
        AdminUserView view = AdminUserView.from(userRepository.save(target));
        auditLogger.adminChange(current.getPublicId(), target.getPublicId(), "role_changed", role.name());
        // Authorities are baked into the session at login: end it so the new role applies now.
        sessionTerminationService.terminateAllSessions(target.getUsername(), target.getPublicId(), "role_changed");
        return view;
    }

    @Transactional
    public void deleteUser(Long targetId, String currentUsername) {
        User current = currentUser(currentUsername);
        User target = findTarget(targetId);
        requireNotSelf(current, target, "account_deleted", "Cannot delete your own account");
        requireNotLastAdmin(current, target, "account_deleted");
        userRepository.delete(target);
        auditLogger.adminChange(current.getPublicId(), target.getPublicId(), "account_deleted", null);
        sessionTerminationService.terminateAllSessions(target.getUsername(), target.getPublicId(), "account_deleted");
    }

    private User currentUser(String currentUsername) {
        return userRepository
                .findByUsername(currentUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Not authenticated"));
    }

    private void requireNotSelf(User current, User target, String change, String message) {
        if (current.getId().equals(target.getId())) {
            auditLogger.adminChangeRejected(current.getPublicId(), target.getPublicId(), change, "self_action");
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.SELF_ACTION, message);
        }
    }

    /** Only relevant when the target is currently an enabled admin, i.e. the change removes one. */
    private void requireNotLastAdmin(User current, User target, String change) {
        if (target.getRole() != Role.ADMIN || !target.isEnabled()) {
            return;
        }
        List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ADMIN);
        if (enabledAdmins.size() <= 1) {
            auditLogger.adminChangeRejected(current.getPublicId(), target.getPublicId(), change, "last_admin");
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.LAST_ADMIN, "Cannot remove the last active administrator");
        }
    }

    private User findTarget(Long targetId) {
        return userRepository
                .findById(targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User not found"));
    }
}
