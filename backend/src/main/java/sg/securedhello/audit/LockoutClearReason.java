package sg.securedhello.audit;

/** Why a password lock was cleared (row 4). Admin unlock and reset redemption add theirs when they land;
 * an admin unlock's own reason goes in {@code user.target.unlock_reason} (REJ-028). */
public enum LockoutClearReason implements AuditReason {

    /** The lock's time ran out; written by the next sign-in that finds it lifted (ADR-011). */
    AUTO_LIFT("AUTO_LIFT");

    private final String code;

    LockoutClearReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
