package sg.securedhello.audit;

/** What a self-registration did with its email address (row 16). The wire answer is the same either way (ADR-032). */
public enum RegistrationReason implements AuditReason {

    /** A new pending registration was created for a new address. */
    NEW_ACCOUNT("NEW_ACCOUNT"),

    /** The address was already known: a pending registration was renewed, or nothing was created. */
    EXISTING_ADDRESS("EXISTING_ADDRESS");

    private final String code;

    RegistrationReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
