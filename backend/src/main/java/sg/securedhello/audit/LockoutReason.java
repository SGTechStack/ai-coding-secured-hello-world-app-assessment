package sg.securedhello.audit;

/** Why an account was locked (row 3). */
public enum LockoutReason implements AuditReason {

    /**
     * Consecutive failures inside the observation window reached the threshold and locked the account's untrusted lane
     * (ADR-011; ADR-012; ADR-075).
     */
    THRESHOLD_REACHED("THRESHOLD_REACHED"),

    /** A trusted device's own failures reached the threshold and locked that device's lane only (ADR-075). */
    TRUSTED_DEVICE_THRESHOLD_REACHED("TRUSTED_DEVICE_THRESHOLD_REACHED");

    private final String code;

    LockoutReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
