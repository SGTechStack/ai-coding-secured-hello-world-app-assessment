package sg.example.helloauth.loginprotection;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.account.AccountService;
import sg.example.helloauth.account.LockoutPolicy;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.email.EmailService;
import sg.example.helloauth.email.EmailService.Recipient;

/**
 * Login protection's first layer: an Account is Locked after repeated failed logins. The
 * Failed-login counter and the lock are stored on the Account, so a restart clears neither.
 * Form login reports each outcome here.
 */
@Component
public class AccountLockout {

    private final AccountService accounts;
    private final AuditLogger audit;
    private final EmailService emails;
    private final LockoutPolicy policy;

    AccountLockout(AccountService accounts, AuditLogger audit, EmailService emails,
            LoginProtectionProperties properties) {
        this.accounts = accounts;
        this.audit = audit;
        this.emails = emails;
        this.policy = new LockoutPolicy(properties.lockout().threshold(), properties.lockout().duration());
    }

    /**
     * Counts a failed login against the Account with the submitted username, if one exists. The
     * failure that locks it is audited, and its owner is told.
     */
    public void loginFailed(String submittedUsername, HttpServletRequest request) {
        if (submittedUsername == null) {
            return;
        }
        accounts.recordFailedLogin(submittedUsername, policy).ifPresent(locked -> accountLocked(locked, request));
    }

    /** Resets the Account's Failed-login counter. */
    public void loginSucceeded(UUID accountId) {
        accounts.recordSuccessfulLogin(accountId);
    }

    private void accountLocked(Account account, HttpServletRequest request) {
        audit.accountLocked(account.getId(), request);
        emails.sendLockoutNotification(new Recipient(account.getId(), account.getEmail()));
    }
}
