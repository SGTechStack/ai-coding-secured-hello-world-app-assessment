package sg.securedhello.audit;

/** Why an admin mutation was refused before it changed anything (row 34; ADR-048). */
public enum AdminRefusalReason implements AuditReason {

    /** Check 1: the admin targeted their own account (REJ-050). */
    SELF_ACTION("SELF_ACTION"),

    /** Check 2: the change would leave fewer than two enrolled admins (R-ADM-008). */
    TWO_ADMIN_INVARIANT("TWO_ADMIN_INVARIANT"),

    /**
     * The guard's lock set could not be taken within the database lock timeout, because other changes held it: the
     * guard never ran, and the caller may retry.
     */
    LOCK_TIMEOUT("LOCK_TIMEOUT");

    private final String code;

    AdminRefusalReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
