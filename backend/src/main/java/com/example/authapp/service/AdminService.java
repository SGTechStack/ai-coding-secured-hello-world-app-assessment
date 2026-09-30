package com.example.authapp.service;

import com.example.authapp.domain.PasswordResetTokenRepository;
import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final SessionService sessions;
    private final AuditLogger audit;

    public AdminService(UserRepository users, PasswordResetTokenRepository tokens, SessionService sessions,
            AuditLogger audit) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return users.findAll();
    }

    @Transactional
    public User setEnabled(String actor, Long targetId, boolean enabled) {
        User target = loadTarget(actor, targetId);
        target.setEnabled(enabled);
        if (!enabled) {
            sessions.invalidateAll(target.getUsername());
        }
        audit.log(enabled ? "user_enabled" : "user_disabled", "actor", actor, "target", target.getUsername());
        return target;
    }

    @Transactional
    public User setRole(String actor, Long targetId, Role role) {
        User target = loadTarget(actor, targetId);
        target.setRole(role);
        // Authorities are cached in the session, so force a re-login to apply the change.
        sessions.invalidateAll(target.getUsername());
        audit.log("role_changed", "actor", actor, "target", target.getUsername(), "role", role);
        return target;
    }

    @Transactional
    public void delete(String actor, Long targetId) {
        User target = loadTarget(actor, targetId);
        tokens.deleteAllByUser(target);
        users.delete(target);
        sessions.invalidateAll(target.getUsername());
        audit.log("user_deleted", "actor", actor, "target", target.getUsername());
    }

    /** Loads the target and enforces that admins cannot act on their own account. */
    private User loadTarget(String actorUsername, Long targetId) {
        User target = users.findById(targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getUsername().equalsIgnoreCase(actorUsername)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admins cannot modify their own account");
        }
        return target;
    }
}
