package com.example.helloauth.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the address to throttle a request against.
 *
 * <p>Uses the socket's peer address and deliberately ignores {@code X-Forwarded-For}. That header is
 * client-supplied: trusting it without a known proxy in front means an attacker can defeat
 * throttling entirely by varying a header value, which is worse than having no throttling at all
 * because it looks like protection. A real deployment behind a load balancer must configure
 * {@code server.forward-headers-strategy} so the container populates the peer address from a
 * trusted hop, at which point this code stays correct without change.
 */
final class ClientAddress {

    private ClientAddress() {}

    static String of(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null ? "unknown" : remoteAddress;
    }
}
