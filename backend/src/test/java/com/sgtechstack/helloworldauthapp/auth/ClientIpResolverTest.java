package com.sgtechstack.helloworldauthapp.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both naive approaches to client-IP resolution are exploitable, in opposite
 * directions, so this pins the behaviour of each configuration explicitly.
 *
 * <p>Trusting {@code X-Forwarded-For} when the app is directly reachable lets a
 * caller rotate a fake value per request and never be throttled. Ignoring it
 * behind a proxy collapses every client into one bucket, so per-client
 * throttling vanishes and one attacker locks out everybody. The hop count is
 * what distinguishes the two.
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    void withNoProxiesIgnoresForwardedForEntirely() {
        // The header is attacker-supplied in this topology, so it must not
        // influence the throttle key at all.
        String resolved = new ClientIpResolver(0)
                .resolve(request("203.0.113.9", "1.2.3.4"));

        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void withNoProxiesASpoofedHeaderCannotChangeTheThrottleKey() {
        ClientIpResolver resolver = new ClientIpResolver(0);

        String first = resolver.resolve(request("203.0.113.9", "10.0.0.1"));
        String second = resolver.resolve(request("203.0.113.9", "10.0.0.2"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void withOneProxyTakesTheClientFromTheForwardedChain() {
        // client -> proxy -> app. The proxy appends the client and becomes the
        // peer, so the chain is [client] and remoteAddr is the proxy.
        String resolved = new ClientIpResolver(1)
                .resolve(request("10.0.0.1", "198.51.100.7"));

        assertThat(resolved).isEqualTo("198.51.100.7");
    }

    @Test
    void withTwoProxiesCountsBackFromTheRight() {
        // client -> p1 -> p2 -> app. Counting from the right matters: the
        // rightmost entries were appended by infrastructure we control, the
        // leftmost is whatever the original caller claimed.
        String resolved = new ClientIpResolver(2)
                .resolve(request("10.0.0.2", "198.51.100.7, 10.0.0.1"));

        assertThat(resolved).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresExtraEntriesPrependedByTheCaller() {
        // A caller who prepends junk shifts the left of the chain but cannot
        // shift the right, so the resolved address is unaffected.
        String resolved = new ClientIpResolver(2)
                .resolve(request("10.0.0.2", "1.1.1.1, 2.2.2.2, 198.51.100.7, 10.0.0.1"));

        assertThat(resolved).isEqualTo("198.51.100.7");
    }

    @Test
    void handlesTheHeaderBeingRepeatedRatherThanCommaSeparated() {
        String resolved = new ClientIpResolver(2)
                .resolve(request("10.0.0.2", "198.51.100.7", "10.0.0.1"));

        assertThat(resolved).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToThePeerWhenTheChainIsShorterThanDeclared() {
        // Either misconfiguration, or someone reached the app by a route that
        // bypasses the proxies. The chain cannot be trusted to hold a client
        // address, so use the one thing that is certain.
        String resolved = new ClientIpResolver(3)
                .resolve(request("203.0.113.9", "198.51.100.7"));

        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void toleratesWhitespaceAndEmptyEntries() {
        String resolved = new ClientIpResolver(1)
                .resolve(request("10.0.0.1", "  198.51.100.7  , "));

        assertThat(resolved).isEqualTo("198.51.100.7");
    }
}
