package sg.securedhello.password;

import java.io.Serial;

/**
 * A new password failed the policy: 400 {@code PASSWORD_REJECTED} with the failing {@link PasswordRule} as its
 * {@code rule} member, written by {@link PasswordRejectedAdvice}. Thrown inside the caller's transaction, which it
 * rolls back, so a rejection changes nothing (and never burns a token, ADR-007).
 */
public final class PasswordRejectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PasswordRule rule;

    public PasswordRejectedException(PasswordRule rule) {
        super("The password was rejected by rule " + rule);
        this.rule = rule;
    }

    public PasswordRule rule() {
        return rule;
    }
}
