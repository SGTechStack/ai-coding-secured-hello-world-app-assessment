package sg.securedhello.admin;

import java.io.Serial;

/**
 * An admin-issued reset asked for an account that cannot redeem one: never activated, whose route is its activation
 * token, or disabled, which cancels every token (ADR-007; R-STD-024). 400 {@code VALIDATION_FAILED}; nothing changes.
 */
public class NotResettableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NotResettableException() {
        super("A reset token is issued only for an activated, enabled account");
    }
}
