package sg.securedhello.security.source;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import sg.securedhello.security.source.ClientIpProperties.Source;

/**
 * The one place a client address becomes a {@link SourceKey} (ADR-020), and the only class allowed to read the raw
 * address (T-RL-028). IPv4 keys on the /32; IPv6 is masked to the configured prefix; IPv4-mapped IPv6 collapses to
 * IPv4 and zone ids are dropped. Parsing is bytes-first and never uses DNS: a token reaches
 * {@link InetAddress#getByName} only after a strict literal shape check that keeps the JDK off its lookup path.
 */
public class SourceKeyResolver {

    /** Counts client addresses keyed as {@link SourceKey#UNPARSEABLE} (R-RL-020). */
    public static final String UNPARSEABLE_COUNTER = "app.client_ip.unparseable";

    /** Longest logged form of an unparseable token (R-RL-020). */
    static final int LOGGED_TOKEN_LIMIT = 64;

    private static final Logger log = LoggerFactory.getLogger(SourceKeyResolver.class);

    private static final String OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";

    /** A strict dotted quad: four decimal octets, no leading zeros. */
    private static final Pattern IPV4 = Pattern.compile(OCTET + "(?:\\." + OCTET + "){3}");

    /**
     * An IPv6 literal's alphabet, containing a colon and starting with a hex digit or a colon. With that shape the JDK
     * parses the token as a literal or throws; it never resolves it.
     */
    private static final Pattern IPV6 = Pattern.compile("(?=.*:)[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    private static final HexFormat HEX = HexFormat.of();

    private final int ipv6PrefixLength;
    private final Level unparseableLevel;
    private final Counter unparseable;

    SourceKeyResolver(ClientIpProperties properties, MeterRegistry meterRegistry) {
        this.ipv6PrefixLength = properties.ipv6PrefixLength();
        // A socket peer is always a literal, so an unparseable one is a defect, not a proxy misconfiguration.
        this.unparseableLevel = properties.source() == Source.PROXY ? Level.WARN : Level.ERROR;
        this.unparseable = meterRegistry.counter(UNPARSEABLE_COUNTER);
        log.info("Source keys: IPv4 /32, IPv6 prefix length {}; client address from {}", ipv6PrefixLength,
                properties.source());
    }

    /** The request's source key, from its client address: the socket peer, or the trusted proxy's client. */
    public SourceKey resolve(HttpServletRequest request) {
        String clientAddress = request.getRemoteAddr();
        SourceKey key = derive(clientAddress, ipv6PrefixLength);
        if (key == SourceKey.UNPARSEABLE) {
            unparseable.increment();
            log.atLevel(unparseableLevel).log("Client address '{}' is not an IP literal; keyed as {}",
                    loggable(clientAddress), SourceKey.UNPARSEABLE.value());
        }
        return key;
    }

    /** The pure derivation: {@code token}'s source key with IPv6 masked to {@code ipv6PrefixLength} bits. */
    static SourceKey derive(String token, int ipv6PrefixLength) {
        InetAddress address = parseLiteral(token);
        if (address == null) {
            return SourceKey.UNPARSEABLE;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return new SourceKey("4:" + HEX.formatHex(bytes));
        }
        for (int i = 0; i < bytes.length; i++) {
            int keptBits = Math.clamp(ipv6PrefixLength - 8L * i, 0, 8);
            bytes[i] &= (byte) (0xFF00 >> keptBits);
        }
        return new SourceKey("6:" + HEX.formatHex(bytes) + "/" + ipv6PrefixLength);
    }

    /** {@code token} as an address if it is an IP literal (any zone id dropped), otherwise {@code null}. No DNS. */
    static InetAddress parseLiteral(String token) {
        if (token == null) {
            return null;
        }
        String literal = token.split("%", 2)[0];
        if (!IPV4.matcher(literal).matches() && !IPV6.matcher(literal).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException malformedLiteral) {
            return null;
        }
    }

    /** {@code token} with every character outside printable ASCII escaped, cut to {@value #LOGGED_TOKEN_LIMIT}. */
    static String loggable(String token) {
        StringBuilder escaped = new StringBuilder();
        String text = String.valueOf(token);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c <= '~' && c != '\\') {
                escaped.append(c);
            } else {
                escaped.append("\\u").append(HEX.toHexDigits(c));
            }
        }
        escaped.setLength(Math.min(escaped.length(), LOGGED_TOKEN_LIMIT));
        return escaped.toString();
    }
}
