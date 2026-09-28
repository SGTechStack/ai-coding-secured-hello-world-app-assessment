package sg.securedhello.audit;

/** The emitter's one home for text neutralisation (ASVS 16.4.1) and the raw-URI cap (REJ-081). */
final class AuditText {

    /** Appended to a capped value, inside the cap, so a truncation is never silent. */
    static final String TRUNCATION_MARKER = "[truncated]";

    private AuditText() {
    }

    /** {@code value} with every CR, LF and pipe removed. */
    static String sanitise(String value) {
        return value.replaceAll("[\r\n|]", "");
    }

    /**
     * {@code value} sanitised and, if longer than {@code maxLength}, cut so that it ends in {@link #TRUNCATION_MARKER}
     * and is exactly {@code maxLength} characters long.
     */
    static String capped(String value, int maxLength) {
        String clean = sanitise(value);
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }
}
