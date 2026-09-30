package sg.securedhello.security.lockout;

import java.util.UUID;

import org.springframework.security.core.AuthenticationException;

import sg.securedhello.audit.LoginFailureReason;

/**
 * A correct password refused after all: the failures deferred under row-lock contention, counted first at their own
 * times, locked the account or disabled its password (ADR-011 amendment of 2026-09-30). The sign-in gets the uniform
 * 401 like any other refusal, and its login-failure row carries the reason.
 */
public final class DeferredFailuresRefusal extends AuthenticationException {

    private final transient UUID userId;
    private final LoginFailureReason reason;

    DeferredFailuresRefusal(UUID userId, LoginFailureReason reason) {
        super("Refused by deferred password failures");
        this.userId = userId;
        this.reason = reason;
    }

    public UUID userId() {
        return userId;
    }

    public LoginFailureReason reason() {
        return reason;
    }
}
