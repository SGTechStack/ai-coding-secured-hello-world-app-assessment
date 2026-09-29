package sg.securedhello.security.source;

import java.io.Serial;
import java.io.Serializable;

/**
 * The details a sign-in's {@code Authentication} carries: the request's {@link SourceKey}, and nothing else. The
 * failure listener has no request, so this is how the lockout-cardinality axis learns which source drove an account
 * into lockout (ADR-015). It replaces Spring's {@code WebAuthenticationDetails}, whose raw address is banned (ADR-020).
 *
 * <p>It is stored in the session with the signed-in {@code Authentication}, so it is serialisable and on the session
 * attribute allowlist; its {@code toString()} names no source, because a source key is an address in another spelling
 * (T-AUD-040).
 *
 * @param sourceKey the source the sign-in came from
 */
public record SourceKeyAuthenticationDetails(SourceKey sourceKey) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "SourceKeyAuthenticationDetails[sourceKey=<redacted>]";
    }
}
