package sg.securedhello.audit;

/** Why a password lock was cleared (row 4). An admin unlock writes row 32 instead, with its own reason in
 * {@code user.target.unlock_reason} (REJ-028). */
public enum LockoutClearReason implements AuditReason {

    /** The lock's time ran out; written by the next sign-in that finds it lifted (ADR-011). */
    AUTO_LIFT("AUTO_LIFT"),

    /** A password-reset redemption rebound the password, which clears the lock and the NIST cap (ADR-009). */
    PASSWORD_RESET_COMPLETED("PASSWORD_RESET_COMPLETED");

    private final String code;

    LockoutClearReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
