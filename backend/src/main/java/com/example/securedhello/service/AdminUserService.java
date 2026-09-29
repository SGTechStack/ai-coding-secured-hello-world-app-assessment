package com.example.securedhello.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Admin account queries and mutations. Reads return entities (the controller
 * maps them to a non-sensitive view). Every mutation enforces the Admin
 * Self-Action Guard — an Admin cannot disable, demote, or delete their own
 * account (acting principal id == target id) — and emits an Audit Event
 * carrying both actor and target.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /** Enables or disables a target account. Rejects self-action. */
    @Transactional
    public void setEnabled(String actorUsername, Long targetId, boolean enabled) {
        User actor = requireActor(actorUsername);
        User target = requireTarget(targetId);
        guardSelfAction(actor, target);

        target.setEnabled(enabled);
        userRepository.save(target);
        auditService.record(enabled ? "ADMIN_ENABLE_USER" : "ADMIN_DISABLE_USER",
                actorUsername, target.getUsername(), "SUCCESS");
    }

    /** Changes a target account's role. Rejects self-action. */
    @Transactional
    public void changeRole(String actorUsername, Long targetId, Role role) {
        User actor = requireActor(actorUsername);
        User target = requireTarget(targetId);
        guardSelfAction(actor, target);

        target.setRole(role);
        userRepository.save(target);
        auditService.record("ADMIN_CHANGE_ROLE", actorUsername, target.getUsername(), "SUCCESS");
    }

    /** Deletes a target account. Rejects self-action. */
    @Transactional
    public void deleteUser(String actorUsername, Long targetId) {
        User actor = requireActor(actorUsername);
        User target = requireTarget(targetId);
        guardSelfAction(actor, target);

        userRepository.delete(target);
        auditService.record("ADMIN_DELETE_USER", actorUsername, target.getUsername(), "SUCCESS");
    }

    private User requireActor(String actorUsername) {
        return userRepository.findByUsername(actorUsername)
                .orElseThrow(AdminTargetNotFoundException::new);
    }

    private User requireTarget(Long targetId) {
        return userRepository.findById(targetId)
                .orElseThrow(AdminTargetNotFoundException::new);
    }

    private void guardSelfAction(User actor, User target) {
        if (actor.getId().equals(target.getId())) {
            throw new SelfActionException();
        }
    }
}
