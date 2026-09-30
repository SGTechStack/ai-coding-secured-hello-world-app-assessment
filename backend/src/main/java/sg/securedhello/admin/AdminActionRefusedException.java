package sg.securedhello.admin;

import java.io.Serial;

import sg.securedhello.audit.AdminRefusalReason;

/**
 * An admin mutation was refused before it changed anything. Thrown inside the guarded transaction, which it rolls
 * back, and written by {@link AdminActionRefusedAdvice}: a self-action as 403 {@code ACCESS_DENIED} (REJ-050), the
 * two-admin invariant as 409 {@code TWO_ADMIN_INVARIANT}, and a lock set that timed out as 503 {@code SERVICE_BUSY}.
 */
public final class AdminActionRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AdminRefusalReason reason;

    public AdminActionRefusedException(AdminRefusalReason reason) {
        super("Admin action refused: " + reason);
        this.reason = reason;
    }

    public AdminRefusalReason reason() {
        return reason;
    }
}
