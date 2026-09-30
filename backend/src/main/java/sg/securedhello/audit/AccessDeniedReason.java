package sg.securedhello.audit;

/** Why a signed-in caller was refused (row 12). */
public enum AccessDeniedReason implements AuditReason {

    /** The caller's role does not grant the route, or the route is denied to everyone (ADR-043). */
    INSUFFICIENT_ROLE("INSUFFICIENT_ROLE");

    private final String code;

    AccessDeniedReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
