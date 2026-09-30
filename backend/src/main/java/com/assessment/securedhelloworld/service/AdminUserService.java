package com.assessment.securedhelloworld.service;

import com.assessment.securedhelloworld.domain.Role;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.exception.ApiException;
import com.assessment.securedhelloworld.repository.UserRepository;
import com.assessment.securedhelloworld.security.AppUserDetails;
import com.assessment.securedhelloworld.web.dto.AdminUserView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-only account management (PRD Stories 8-11). Every mutating operation (enable/disable,
 * role change, delete) routes through the single {@link #requireNotSelf(Long, AppUserDetails)}
 * guard rather than each re-implementing the same check, so "an admin cannot act on their own
 * account" is enforced in exactly one place.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AdminUserService(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public List<AdminUserView> listUsers() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(AdminUserService::toView)
                .toList();
    }

    @Transactional
    public AdminUserView setEnabled(Long targetId, boolean enabled, AppUserDetails actor) {
        requireNotSelf(targetId, actor);
        User target = loadUser(targetId);
        target.setEnabled(enabled);
        userRepository.save(target);
        auditLogService.event(enabled ? "admin_user_enabled" : "admin_user_disabled", "success",
                actor.getUsername(), target.getUsername());
        return toView(target);
    }

    @Transactional
    public AdminUserView changeRole(Long targetId, Role newRole, AppUserDetails actor) {
        requireNotSelf(targetId, actor);
        User target = loadUser(targetId);
        target.setRole(newRole);
        userRepository.save(target);
        auditLogService.event("admin_role_change", "success", actor.getUsername(), target.getUsername());
        return toView(target);
    }

    @Transactional
    public void deleteUser(Long targetId, AppUserDetails actor) {
        requireNotSelf(targetId, actor);
        User target = loadUser(targetId);
        userRepository.delete(target);
        auditLogService.event("admin_user_deleted", "success", actor.getUsername(), target.getUsername());
    }

    /**
     * The one shared guard reused by every mutating admin action (PRD Stories 9-11): an admin can
     * never enable/disable, re-role, or delete their own account through these endpoints.
     */
    private void requireNotSelf(Long targetId, AppUserDetails actor) {
        if (targetId.equals(actor.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_ACTION_FORBIDDEN",
                    "An admin cannot perform this action on their own account");
        }
    }

    private User loadUser(Long targetId) {
        return userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private static AdminUserView toView(User user) {
        return new AdminUserView(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
