package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import org.springframework.stereotype.Component;

/** Carries the loaded account's identity from the synchronous DAO lookup to the login outcome handler. */
@Component
final class AuthenticationAttemptContext {

    private final ThreadLocal<Attempt> current = new ThreadLocal<>();

    void start() {
        current.set(new Attempt());
    }

    void accountLoaded(AuditAccount account) {
        Attempt attempt = current.get();
        if (attempt != null) {
            attempt.account = account;
        }
    }

    /** The account the attempt's username belongs to, or null if none was loaded. */
    AuditAccount knownAccount() {
        Attempt attempt = current.get();
        return attempt != null ? attempt.account : null;
    }

    void clear() {
        current.remove();
    }

    private static final class Attempt {
        private AuditAccount account;
    }
}
