package sg.securedhello.email;

import java.net.URI;

import sg.securedhello.credential.CredentialTokenType;

/**
 * A credential link for one recipient: an activation link or a password-reset link. The link carries a bearer
 * token, so {@link #toString()} leaves it out.
 *
 * @param type      which kind of token the link redeems
 * @param recipient the canonical email address it goes to
 * @param link      the link, on the configured link origin
 */
public record LinkEmail(CredentialTokenType type, String recipient, URI link) {

    @Override
    public String toString() {
        return "LinkEmail[type=" + type + ", link=<redacted>]";
    }
}
