package sg.securedhello.audit;

/** Why an account was locked (row 3). */
public enum LockoutReason implements AuditReason {

    /** Consecutive failures inside the observation window reached the threshold (ADR-011; ADR-012). */
    THRESHOLD_REACHED("THRESHOLD_REACHED");

    private final String code;

    LockoutReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
