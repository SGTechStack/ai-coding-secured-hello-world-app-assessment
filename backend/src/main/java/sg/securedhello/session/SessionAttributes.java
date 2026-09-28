package sg.securedhello.session;

/** Names of the application's own session attributes. */
public final class SessionAttributes {

    /**
     * The instant of password sign-in, the anchor of the absolute lifetime (ADR-038). Stamped in exactly one place,
     * the login composite. A session without it is anonymous (R-SES-010).
     */
    public static final String AUTH_INSTANT = "AUTH_INSTANT";

    private SessionAttributes() {
    }
}
