package sg.securedhello.security.source;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.Nullable;

/**
 * The details a sign-in's {@code Authentication} carries: the request's {@link SourceKey}, and the device claim of its
 * device cookie, if one verified (ADR-075). The listeners have no request, so this is how the lockout-cardinality axis
 * learns which source drove an account into lockout (ADR-015), and how the lockout learns which lane a sign-in counts
 * in. It replaces Spring's {@code WebAuthenticationDetails}, whose raw address is banned (ADR-020).
 *
 * <p>It is stored in the session with the signed-in {@code Authentication}, so it is serialisable and on the session
 * attribute allowlist; its {@code toString()} names no source, because a source key is an address in another spelling
 * (T-AUD-040), and no device.
 *
 * @param sourceKey the source the sign-in came from
 * @param device    what its device cookie claims, or {@code null} if it presented none that verified
 */
public record SourceKeyAuthenticationDetails(SourceKey sourceKey, @Nullable DeviceClaim device)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Details with no device claim. */
    public SourceKeyAuthenticationDetails(SourceKey sourceKey) {
        this(sourceKey, null);
    }

    @Override
    public String toString() {
        return "SourceKeyAuthenticationDetails[sourceKey=<redacted>, device=<redacted>]";
    }
}
