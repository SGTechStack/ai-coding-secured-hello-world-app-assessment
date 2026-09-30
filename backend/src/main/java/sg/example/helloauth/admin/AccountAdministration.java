package sg.example.helloauth.admin;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.account.AccountService;
import sg.example.helloauth.account.Role;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.passwordreset.PasswordResetService;
import sg.example.helloauth.session.SessionControl;

/**
 * What an Admin may do to another Account. Two rules hold for every action: an Admin never acts
 * on their own Account, so the acting Admin always remains one (ADR-0008), and a target that is
 * unknown or a Tombstone is not found. A change that takes access or privilege away ends the
 * target's sessions at once, rather than at their next login.
 */
@Service
class AccountAdministration {

    private final AccountService accounts;
    private final SessionControl sessionControl;
    private final PasswordResetService passwordReset;
    private final AuditLogger audit;
    private final Clock clock;

    AccountAdministration(AccountService accounts, SessionControl sessionControl,
            PasswordResetService passwordReset, AuditLogger audit, Clock clock) {
        this.accounts = accounts;
        this.sessionControl = sessionControl;
        this.passwordReset = passwordReset;
        this.audit = audit;
        this.clock = clock;
    }

    /** Disabling ends the target's sessions; re-enabling leaves any lock in place. */
    AccountView setEnabled(UUID adminId, UUID targetId, boolean enabled, HttpServletRequest request) {
        Account account = administer(adminId, targetId, request, () -> accounts.setEnabled(targetId, enabled));
        if (enabled) {
            audit.accountEnabled(adminId, targetId, request);
        } else {
            sessionControl.endAllSessions(account.getUsername());
            audit.accountDisabled(adminId, targetId, request);
        }
        return view(account);
    }

    /** Lifts the lock and resets the Failed-login counter, so the owner can log in straight away. */
    AccountView unlock(UUID adminId, UUID targetId, HttpServletRequest request) {
        Account account = administer(adminId, targetId, request, () -> accounts.unlock(targetId));
        audit.accountUnlocked(adminId, targetId, request);
        return view(account);
    }

    /** Ends the target's sessions, so a demoted Admin can't keep their old rights in one. */
    AccountView changeRole(UUID adminId, UUID targetId, Role role, HttpServletRequest request) {
        Account account = administer(adminId, targetId, request, () -> accounts.changeRole(targetId, role));
        sessionControl.endAllSessions(account.getUsername());
        audit.roleChanged(adminId, targetId, role.name(), request);
        return view(account);
    }

    /**
     * Makes the target a Tombstone (ADR-0007): it can't log in and is hidden everywhere, its
     * sessions end, and its Password reset tokens are removed.
     */
    void delete(UUID adminId, UUID targetId, HttpServletRequest request) {
        Account account = administer(adminId, targetId, request, () -> accounts.softDelete(targetId));
        sessionControl.endAllSessions(account.getUsername());
        passwordReset.removeTokens(targetId);
        audit.accountDeleted(adminId, targetId, request);
    }

    private Account administer(UUID adminId, UUID targetId, HttpServletRequest request,
            Supplier<Optional<Account>> change) {
        if (adminId.equals(targetId)) {
            audit.selfAdministrationRejected(adminId, request);
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.SELF_ACTION_NOT_ALLOWED,
                    "Admins can't make this change to their own account.");
        }
        return change.get().orElseThrow(() -> {
            audit.administrationTargetNotFound(adminId, targetId, request);
            return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "There is no such account.");
        });
    }

    private AccountView view(Account account) {
        return AccountView.of(account, clock.instant());
    }
}
