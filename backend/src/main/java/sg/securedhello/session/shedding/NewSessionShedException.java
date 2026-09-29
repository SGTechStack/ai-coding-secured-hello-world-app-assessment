package sg.securedhello.session.shedding;

/** A session-less {@code GET /api/csrf} arrived during a shed episode, so no session is created (ADR-041). */
public class NewSessionShedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NewSessionShedException() {
        super("New anonymous sessions are being shed", null, false, false);
    }
}
