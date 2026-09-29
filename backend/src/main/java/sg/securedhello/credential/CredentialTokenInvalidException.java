package sg.securedhello.credential;

import java.io.Serial;

/**
 * A submitted credential token did not redeem: unknown, of another type, already used or expired. Every such failure
 * is one answer, 400 {@code RESET_TOKEN_INVALID}, whatever the token's type, so an anonymous caller never learns which
 * kind of token it holds (ADR-032).
 */
public class CredentialTokenInvalidException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CredentialTokenInvalidException() {
        super("The credential token did not redeem");
    }
}
