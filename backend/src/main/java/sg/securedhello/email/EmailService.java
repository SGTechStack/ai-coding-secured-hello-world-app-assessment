package sg.securedhello.email;

/**
 * Delivers a credential link to an account's email address. There is no mail transport: the one implementation,
 * {@link DevLinkLogger}, writes the link to the dev-only link logger (ADR-057), and the test contexts replace it with a
 * capture (ADR-067). A gate that must know whether transport exists keys on a declared property, never on this bean's
 * type.
 */
public interface EmailService {

    /** Sends {@code email}. It must not throw for a delivery problem: the caller's answer never depends on it. */
    void send(LinkEmail email);
}
