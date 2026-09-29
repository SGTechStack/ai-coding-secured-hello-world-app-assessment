package com.example.hello.admin;

import com.example.hello.auth.AuthPrincipal;
import com.example.hello.auth.SessionRevocation;
import com.example.hello.shared.ApiException;
import com.example.hello.shared.AuditLog;
import com.example.hello.user.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final UserRepository users;
    private final SessionRevocation sessions;
    private final AuditLog audit;

    public AdminService(UserRepository users, SessionRevocation sessions, AuditLog audit) {
        this.users = users; this.sessions = sessions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() { return users.findAll(Sort.by("createdAt", "username")).stream().map(UserView::from).toList(); }

    @Transactional
    public UserView status(AuthPrincipal actor, UUID id, boolean enabled) {
        UserAccount target = target(actor, id);
        target.changeEnabled(enabled);
        sessions.revoke(target.getUsername());
        audit.record(enabled ? "enable" : "disable", actor.username(), target.getUsername(), "success");
        return UserView.from(target);
    }

    @Transactional
    public UserView role(AuthPrincipal actor, UUID id, Role role) {
        UserAccount target = target(actor, id);
        target.changeRole(role);
        sessions.revoke(target.getUsername());
        audit.record("role_change", actor.username(), target.getUsername(), role.name());
        return UserView.from(target);
    }

    @Transactional
    public void delete(AuthPrincipal actor, UUID id) {
        UserAccount target = target(actor, id);
        sessions.revoke(target.getUsername());
        users.delete(target);
        audit.record("delete", actor.username(), target.getUsername(), "success");
    }

    private UserAccount target(AuthPrincipal actor, UUID id) {
        if (actor.id().equals(id)) throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot modify or delete your own account.");
        return users.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
