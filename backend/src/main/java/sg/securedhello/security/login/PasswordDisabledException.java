package sg.securedhello.security.login;

import java.util.UUID;

import org.springframework.security.core.AuthenticationException;

/**
 * The NIST cap has disabled the account's password authenticator (ADR-013). Its own exception, not
 * {@code DisabledException}: an admin disable and a cap disable must stay apart (REJ-020). It extends no mapped
 * exception, and {@code DefaultAuthenticationEventPublisher} maps by exact class name with no default, so the refusal
 * publishes no failure event and moves no counter (T-LCK-009). Its login-failure row is written by the failure
 * handler instead, from {@link #userId()}.
 */
final class PasswordDisabledException extends AuthenticationException {

    private final UUID userId;

    PasswordDisabledException(UUID userId) {
        super("The account's password is disabled until it is rebound");
        this.userId = userId;
    }

    UUID userId() {
        return userId;
    }
}
