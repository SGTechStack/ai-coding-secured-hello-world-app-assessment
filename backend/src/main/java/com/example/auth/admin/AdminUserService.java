package com.example.auth.admin;

import com.example.auth.audit.AuditLogger;
import com.example.auth.auth.ApiException;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public AdminUserService(UserRepository userRepository, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        return userRepository.findAll().stream().map(AdminUserView::from).toList();
    }

    @Transactional
    public AdminUserView setEnabled(Long targetId, boolean enabled, String currentUsername) {
        requireNotSelf(targetId, currentUsername, "Cannot change your own account status");
        User target = findTarget(targetId);
        target.setEnabled(enabled);
        AdminUserView view = AdminUserView.from(userRepository.save(target));
        if (enabled) {
            auditLogger.accountEnabled(currentUsername, target.getUsername());
        } else {
            auditLogger.accountDisabled(currentUsername, target.getUsername());
        }
        return view;
    }

    @Transactional
    public AdminUserView setRole(Long targetId, Role role, String currentUsername) {
        if (role == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role must be provided");
        }
        requireNotSelf(targetId, currentUsername, "Cannot change your own role");
        User target = findTarget(targetId);
        target.setRole(role);
        AdminUserView view = AdminUserView.from(userRepository.save(target));
        auditLogger.roleChanged(currentUsername, target.getUsername(), role.name());
        return view;
    }

    /** Returns the deleted user's username so the caller can expire their sessions afterward. */
    @Transactional
    public String deleteUser(Long targetId, String currentUsername) {
        requireNotSelf(targetId, currentUsername, "Cannot delete your own account");
        User target = findTarget(targetId);
        userRepository.delete(target);
        auditLogger.accountDeleted(currentUsername, target.getUsername());
        return target.getUsername();
    }

    private void requireNotSelf(Long targetId, String currentUsername, String message) {
        User current = userRepository
                .findByUsername(currentUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (current.getId().equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private User findTarget(Long targetId) {
        return userRepository.findById(targetId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
