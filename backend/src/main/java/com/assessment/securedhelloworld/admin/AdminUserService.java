package com.assessment.securedhelloworld.admin;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserView::from)
                .toList();
    }

    @Transactional
    public void updateEnabled(Long targetUserId, boolean enabled, AppUserDetails actingAdmin) {
        User target = requireUser(targetUserId);
        requireNotSelf(target, actingAdmin, "An admin cannot disable their own account");

        target.setEnabled(enabled);
        userRepository.save(target);
        log.info("Admin action: {} actor={} target={}",
                enabled ? "enable" : "disable", actingAdmin.getUsername(), target.getUsername());
    }

    @Transactional
    public void updateRole(Long targetUserId, Role newRole, AppUserDetails actingAdmin) {
        User target = requireUser(targetUserId);
        requireNotSelf(target, actingAdmin, "An admin cannot change their own role");

        target.setRole(newRole);
        userRepository.save(target);
        log.info("Admin action: role-change actor={} target={} newRole={}",
                actingAdmin.getUsername(), target.getUsername(), newRole);
    }

    @Transactional
    public void deleteUser(Long targetUserId, AppUserDetails actingAdmin) {
        User target = requireUser(targetUserId);
        requireNotSelf(target, actingAdmin, "An admin cannot delete their own account");

        userRepository.delete(target);
        log.info("Admin action: delete actor={} target={}", actingAdmin.getUsername(), target.getUsername());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No such user: " + userId));
    }

    private void requireNotSelf(User target, AppUserDetails actingAdmin, String message) {
        if (target.getId().equals(actingAdmin.getUser().getId())) {
            throw new SelfActionForbiddenException(message);
        }
    }
}
