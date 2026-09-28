package sg.securedhello.security.source;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the client address comes from, and how IPv6 addresses aggregate into source keys (ADR-020; R-RL-007).
 * Validated at context refresh, so a bad value stops startup.
 *
 * @param source           {@code socket} (the default): the socket peer; {@code proxy}: the {@code X-Forwarded-For}
 *                         client, honoured only from a peer named in {@code trustedProxies}
 * @param trustedProxies   the proxy addresses, as IP literals; no default, and required when {@code source=proxy}
 * @param ipv6PrefixLength the IPv6 source-key prefix, 48 to 128, default 64
 */
@Validated
@ConfigurationProperties("app.security.client-ip")
public record ClientIpProperties(
        @DefaultValue("socket") @NotNull Source source,
        List<String> trustedProxies,
        @DefaultValue("64") @Min(48) @Max(128) int ipv6PrefixLength) {

    public ClientIpProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }

    /** Where the client address comes from. */
    public enum Source {
        /** The socket peer; forwarded headers are ignored. */
        SOCKET,
        /** The forwarded client, from a named trusted proxy only. */
        PROXY
    }

    /** {@code source=proxy} with no named proxy would key every request on the proxy's address (R-RL-007). */
    @AssertTrue(message = "source=proxy needs trusted-proxies to name each proxy address")
    public boolean isProxySourceNamed() {
        return source != Source.PROXY || !trustedProxies.isEmpty();
    }

    /** Proxies are named by address; a hostname would need DNS, and a pattern could trust a neighbour. */
    @AssertTrue(message = "every trusted-proxies entry must be an IP literal")
    public boolean isTrustedProxiesLiteral() {
        return trustedProxies.stream().allMatch(proxy -> SourceKeyResolver.parseLiteral(proxy) != null);
    }
}
