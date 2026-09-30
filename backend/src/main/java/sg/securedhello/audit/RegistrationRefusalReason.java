package sg.securedhello.audit;

/** Why a self-registration was refused (row 17). */
public enum RegistrationRefusalReason implements AuditReason {

    /** An account, a tombstone or another address's live hold has the username (ADR-032). */
    USERNAME_UNAVAILABLE("USERNAME_UNAVAILABLE");

    private final String code;

    RegistrationRefusalReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
