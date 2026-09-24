package com.example.helloauth.admin;

import com.example.helloauth.audit.AuditLogger;
import com.example.helloauth.passwordreset.PasswordResetTokenRepository;
import com.example.helloauth.session.SessionInvalidationService;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user-management operations (ticket 13). Every mutation takes the
 * acting admin's username so the self-action guard lives one layer below the
 * HTTP mapping — an admin can never disable, demote, or delete their own
 * account (that could lock out the only admin).
 *
 * <p>Session invalidation runs through {@link SessionInvalidationService}
 * (the ticket-02 mechanism) whenever a change would otherwise leave a live
 * session holding more privilege than
 * the account now has: disable, delete, and ADMIN→USER demotion. A stored
 * {@code SecurityContext} keeps its original authorities for the session's
 * lifetime, so a demoted admin's session would stay admin — invalidating it
 * is the fail-closed choice. (Promotion and re-enable need no invalidation:
 * the session can only hold <em>less</em> privilege; the new powers arrive
 * at next login.)
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final SessionInvalidationService sessionInvalidation;
    private final AuditLogger audit;

    public AdminService(UserRepository users,
            PasswordResetTokenRepository tokens,
            SessionInvalidationService sessionInvalidation,
            AuditLogger audit) {
        this.users = users;
        this.tokens = tokens;
        this.sessionInvalidation = sessionInvalidation;
        this.audit = audit;
    }

    /** Every account, in stable id order — password hashes never leave the entity. */
    public List<AdminUserResponse> listUsers() {
        return users.findAll(Sort.by("id")).stream()
            .map(AdminUserResponse::from)
            .toList();
    }

    /**
     * Sets {@code enabled} on another account. Disabling also kills the
     * target's sessions — suspension is meaningless while a live session
     * still works. {@code @Transactional} so the flag update and the Spring
     * Session JDBC deletes land atomically.
     */
    @Transactional
    public AdminUserResponse setEnabled(String actorUsername, Long id, boolean enabled) {
        User target = requireOtherAccount(actorUsername, id, "change the status of");
        boolean changed = target.isEnabled() != enabled;
        target.setEnabled(enabled);
        users.save(target);
        if (!enabled) {
            sessionInvalidation.invalidateAllFor(target.getUsername());
        }
        // Audit only a real change — a no-op PATCH asserting "changed" would
        // be a false event (and it still lands afterCommit via AuditLogger).
        if (changed) {
            audit.adminStatusChanged(actorUsername, target.getUsername(), enabled);
        }
        return AdminUserResponse.from(target);
    }

    /**
     * Switches the target's role between {@code USER} and {@code ADMIN}. A
     * demotion invalidates the target's sessions — see the class javadoc.
     */
    @Transactional
    public AdminUserResponse setRole(String actorUsername, Long id, Role role) {
        User target = requireOtherAccount(actorUsername, id, "change the role of");
        boolean changed = target.getRole() != role;
        boolean demotion = target.getRole() == Role.ADMIN && role == Role.USER;
        target.setRole(role);
        users.save(target);
        if (demotion) {
            sessionInvalidation.invalidateAllFor(target.getUsername());
        }
        if (changed) {
            audit.adminRoleChanged(actorUsername, target.getUsername(), role.name());
        }
        return AdminUserResponse.from(target);
    }

    /**
     * Removes another account entirely: its reset tokens first (the
     * {@code user_id} FK would otherwise reject the delete), then its
     * sessions, then the row — one transaction, so either everything is
     * gone or nothing is.
     */
    @Transactional
    public void deleteUser(String actorUsername, Long id) {
        User target = requireOtherAccount(actorUsername, id, "delete");
        tokens.deleteByUser(target);
        sessionInvalidation.invalidateAllFor(target.getUsername());
        users.delete(target);
        audit.adminUserDeleted(actorUsername, target.getUsername());
    }

    /**
     * The self-action guard plus the 404 lookup, shared by every mutation.
     * Self-targeting compares usernames (unique) rather than ids — the
     * principal's id is not on the {@code Authentication}.
     */
    private User requireOtherAccount(String actorUsername, Long id, String action) {
        User target = users.findById(id)
            .orElseThrow(() -> new AdminException.UserNotFound(id));
        if (target.getUsername().equals(actorUsername)) {
            throw new AdminException.SelfAction(action);
        }
        return target;
    }
}
