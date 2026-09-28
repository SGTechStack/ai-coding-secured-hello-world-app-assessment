package com.example.helloauth.service;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.repository.PasswordResetTokenRepository;
import com.example.helloauth.service.exception.AuthExceptions.AccountNotFoundException;
import com.example.helloauth.service.exception.AuthExceptions.SelfActionForbiddenException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration.
 *
 * <p>Each mutation refuses to act on the actor's own account. The guard is here rather than in the
 * controller because it is a rule about the operation, not about the request — three endpoints share
 * it and none of them should be able to forget it.
 *
 * <p>Each mutation also ends the target's sessions. The PRD does not ask for that, but the
 * alternative is worse than it looks: authorities are resolved at login and cached in the session,
 * so a disabled account would keep working until its session expired, and a demoted admin would
 * keep admin rights for up to half an hour. "A disabled user can no longer log in" is satisfied
 * either way; "a disabled user can no longer do anything" needs this.
 */
@Service
public class AdminAccountService {

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final SessionService sessions;
    private final AuditLog audit;

    public AdminAccountService(
            AccountRepository accounts,
            PasswordResetTokenRepository tokens,
            SessionService sessions,
            AuditLog audit) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accounts.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Account setEnabled(UUID targetId, boolean enabled, String actorUsername) {
        Account target = requireOther(targetId, actorUsername, "You cannot change your own status.");
        target.setEnabled(enabled);
        if (!enabled) {
            sessions.endAllSessionsFor(target.getUsername());
        }
        audit.accountStatusChanged(actorUsername, target.getUsername(), enabled);
        return target;
    }

    @Transactional
    public Account setRole(UUID targetId, Role role, String actorUsername) {
        Account target = requireOther(targetId, actorUsername, "You cannot change your own role.");
        target.setRole(role);
        // The new role only takes effect on the next login, so the current one has to end.
        sessions.endAllSessionsFor(target.getUsername());
        audit.accountRoleChanged(actorUsername, target.getUsername(), role);
        return target;
    }

    @Transactional
    public void delete(UUID targetId, String actorUsername) {
        Account target = requireOther(targetId, actorUsername, "You cannot delete your own account.");
        sessions.endAllSessionsFor(target.getUsername());
        // No cascade on the mapping, so the tokens go first or the foreign key refuses.
        tokens.deleteByAccount(target);
        accounts.delete(target);
        audit.accountDeleted(actorUsername, target.getUsername());
    }

    /**
     * Loads the target and refuses if it is the actor.
     *
     * <p>Compares database identities rather than usernames from the request, so the check cannot be
     * sidestepped by anything the client controls.
     */
    private Account requireOther(UUID targetId, String actorUsername, String message) {
        Account target = accounts.findById(targetId).orElseThrow(AccountNotFoundException::new);
        Account actor =
                accounts.findByUsername(actorUsername).orElseThrow(AccountNotFoundException::new);
        if (target.getId().equals(actor.getId())) {
            throw new SelfActionForbiddenException(message);
        }
        return target;
    }
}
