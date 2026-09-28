package sg.securedhello.security.source;

/**
 * What every per-source control and log field treats as "a source" (ADR-020). The text form is pinned because it is
 * an HMAC input: {@code 4:<8 lower-case hex>} for IPv4, {@code 6:<32 lower-case hex>/<n>} for an IPv6 address masked
 * to {@code n} bits, or {@code unparseable} for the one shared bucket of tokens that are not IP literals.
 *
 * @param value the pinned text form
 */
public record SourceKey(String value) {

    /** The shared bucket for a client address that is not an IP literal (R-RL-020). */
    public static final SourceKey UNPARSEABLE = new SourceKey("unparseable");
}
