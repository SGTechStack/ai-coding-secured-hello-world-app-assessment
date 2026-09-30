package sg.example.helloauth.api;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Who a request comes from, for throttling and audit. It is the connection's peer, unless that
 * peer is a configured trusted proxy: then it is the last address in {@code X-Forwarded-For}
 * that isn't one of our proxies. A client can put anything in that header, but only what a
 * trusted proxy appended is believed, so forging it gains nothing.
 */
@Component
public class ClientIpResolver {

    /** Only IP literals are matched: the matcher would look up a host name in DNS. */
    private static final Pattern IP_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

    private final List<IpAddressMatcher> trustedProxies;

    ClientIpResolver(ApiProperties api) {
        this.trustedProxies = api.trustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    public String clientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || !isTrustedProxy(peer)) {
            return peer;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return peer;
    }

    private boolean isTrustedProxy(String address) {
        if (!IP_LITERAL.matcher(address).matches()) {
            return false;
        }
        try {
            return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
