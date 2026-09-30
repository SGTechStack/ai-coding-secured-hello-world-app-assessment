package com.assessment.hello.service;

import com.assessment.hello.domain.Role;
import com.assessment.hello.domain.User;
import com.assessment.hello.dto.UserView;
import com.assessment.hello.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        return userRepository.findAll().stream()
                .map(UserView::from)
                .toList();
    }

    @Transactional
    public UserView setEnabled(String actorUsername, UUID targetId, boolean enabled) {
        User target = findTarget(targetId);
        guardSelfAction(actorUsername, target, "enable/disable");
        target.setEnabled(enabled);
        userRepository.save(target);
        log.info("Admin action actor={} target={} enabled={}",
                actorUsername, target.getUsername(), enabled);
        return UserView.from(target);
    }

    @Transactional
    public UserView changeRole(String actorUsername, UUID targetId, Role role) {
        User target = findTarget(targetId);
        guardSelfAction(actorUsername, target, "role-change");
        target.setRole(role);
        userRepository.save(target);
        log.info("Admin action actor={} target={} newRole={}",
                actorUsername, target.getUsername(), role);
        return UserView.from(target);
    }

    @Transactional
    public void deleteUser(String actorUsername, UUID targetId) {
        User target = findTarget(targetId);
        guardSelfAction(actorUsername, target, "delete");
        userRepository.delete(target);
        log.info("Admin action actor={} deleted target={}", actorUsername, target.getUsername());
    }

    private User findTarget(UUID targetId) {
        return userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void guardSelfAction(String actorUsername, User target, String action) {
        if (target.getUsername().equals(actorUsername)) {
            log.warn("Blocked admin self-{} actor={}", action, actorUsername);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Admins cannot perform this action on their own account");
        }
    }
}
