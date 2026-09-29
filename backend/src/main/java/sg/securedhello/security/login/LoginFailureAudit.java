package sg.securedhello.security.login;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.AuthenticationException;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.LoginFailureReason;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Writes the login-failure row (row 2) from the provider's failure event, with the internal reason the wire never
 * shows (ADR-033). The account is looked up here, after authentication has failed, never before it: a lookup ahead of
 * the provider would put an existence branch in front of its timing mitigation (REJ-042). {@code user.id} is set
 * explicitly when the account resolves, and omitted when it does not (R-AUD-007).
 */
final class LoginFailureAudit {

    private final UserAccountRepository accounts;
    private final AuditEmitter audit;

    LoginFailureAudit(UserAccountRepository accounts, AuditEmitter audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @EventListener
    void loginFailed(AbstractAuthenticationFailureEvent event) {
        Optional<UserAccount> account = accounts.findByUsername(event.getAuthentication().getName());
        UUID userId = account.map(UserAccount::getId).orElse(null);
        audit.emit(AuditEvent.LOGIN_FAILURE,
                AccountContext.loginFailure(userId, reason(event.getException(), account)));
    }

    static LoginFailureReason reason(AuthenticationException failure, Optional<UserAccount> account) {
        if (account.isEmpty()) {
            return LoginFailureReason.UNKNOWN_USER;
        }
        if (failure instanceof LockedException) {
            return LoginFailureReason.ACCOUNT_LOCKED;
        }
        if (failure instanceof CredentialsExpiredException) {
            return LoginFailureReason.CREDENTIAL_EXPIRED;
        }
        // A never-activated account has no password and is reported to the provider as not found.
        if (failure instanceof DisabledException || account.get().getPasswordHash() == null) {
            return LoginFailureReason.ACCOUNT_DISABLED;
        }
        return LoginFailureReason.BAD_CREDENTIALS;
    }
}
