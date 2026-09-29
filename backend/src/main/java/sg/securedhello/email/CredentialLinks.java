package sg.securedhello.email;

import java.net.URI;

import org.springframework.stereotype.Component;

import sg.securedhello.config.OriginsProperties;
import sg.securedhello.credential.CredentialTokenType;

/**
 * Builds credential links on the link origin, {@code app.origins.spa}, which comes only from configuration and never
 * from {@code Host}, {@code X-Forwarded-*} or a request field (REJ-022). The token travels in the fragment, which a
 * browser never sends to a server, so it stays out of access logs and {@code Referer} headers.
 */
@Component
public class CredentialLinks {

    private final String origin;

    CredentialLinks(OriginsProperties origins) {
        this.origin = origins.spa();
    }

    /** The email that delivers a {@code type} {@code token} to {@code recipient}. */
    public LinkEmail email(CredentialTokenType type, String recipient, String token) {
        String page = switch (type) {
            case ACTIVATION -> "/activate";
            case PASSWORD_RESET -> "/reset";
        };
        return new LinkEmail(type, recipient, URI.create(origin + page + "#token=" + token));
    }
}
