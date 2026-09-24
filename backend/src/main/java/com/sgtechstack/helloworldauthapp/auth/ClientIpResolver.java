package com.sgtechstack.helloworldauthapp.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out which address to attribute a request to, for throttling.
 *
 * <p>This is deliberately explicit rather than automatic, because both of the
 * obvious approaches are wrong in one deployment and right in the other:
 *
 * <ul>
 *   <li>Using {@code getRemoteAddr()} unconditionally — what this application
 *       did before — means that behind any proxy every client collapses into a
 *       single key. Per-client throttling disappears entirely, and worse, the
 *       tenth failed login from <em>anyone</em> throttles <em>everyone</em>
 *       behind that proxy. A brute-force control becomes a denial of service.</li>
 *   <li>Trusting {@code X-Forwarded-For} unconditionally is worse. The header
 *       is attacker-supplied when the app is directly reachable, so a caller
 *       can rotate a fake value per request and never be throttled at all.</li>
 * </ul>
 *
 * <p>So the topology has to be declared. {@code app.security.trusted-proxy-hops}
 * is the number of proxies between the internet and this application:
 *
 * <ul>
 *   <li><strong>0 (default)</strong> — directly reachable.
 *       {@code X-Forwarded-For} is ignored completely, because nothing can
 *       vouch for it.</li>
 *   <li><strong>N &gt; 0</strong> — the client address is taken N hops back
 *       from the right of the chain. Counting from the right matters: the
 *       rightmost entries were appended by infrastructure we control, while
 *       the leftmost is whatever the original caller claimed.</li>
 * </ul>
 *
 * <p>This pairs with {@code server.forward-headers-strategy}, which governs
 * the same question for the framework's own view of the request. Both are left
 * off by default for the same reason.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.security.trusted-proxy-hops:0}") int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public String resolve(HttpServletRequest request) {
        String immediatePeer = request.getRemoteAddr();

        if (trustedProxyHops <= 0) {
            return immediatePeer;
        }

        List<String> chain = forwardedChain(request);
        // The immediate peer is the last hop and is never self-reported, so it
        // belongs on the end of the chain rather than being taken on trust from
        // a header.
        chain.add(immediatePeer);

        int clientIndex = chain.size() - 1 - trustedProxyHops;

        if (clientIndex < 0) {
            // Fewer hops arrived than were declared. Either the configuration
            // is wrong or someone reached the app by a path that bypasses the
            // proxies. Either way the chain cannot be trusted to contain a
            // client address, so fall back to the one thing that is certain.
            log.warn("X-Forwarded-For chain shorter than trusted-proxy-hops={} (chain size {}); "
                    + "falling back to the immediate peer", trustedProxyHops, chain.size());
            return immediatePeer;
        }

        return chain.get(clientIndex);
    }

    private static List<String> forwardedChain(HttpServletRequest request) {
        List<String> chain = new ArrayList<>();

        // Comma-separated within a header, and the header may also be repeated.
        for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String entry : header.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    chain.add(trimmed);
                }
            }
        }

        return chain;
    }
}
