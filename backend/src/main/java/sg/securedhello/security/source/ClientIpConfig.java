package sg.securedhello.security.source;

import static java.util.stream.Collectors.joining;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import sg.securedhello.security.source.ClientIpProperties.Source;

/**
 * The source-key resolver, and the one forwarded-header trust in the application (REJ-015; R-RL-007). With
 * {@code source=proxy}, Tomcat's {@link RemoteIpValve} takes the client from {@code X-Forwarded-For}, but only when
 * the socket peer is one of the named proxies. The names map to {@code internalProxies}, which strips them from the
 * chain, never to {@code trustedProxies}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    /** The audit keying window, which also paces the unparseable-address line. */
    static final String KEYING_WINDOW = "app.audit.keying.window";

    private static final Duration DEFAULT_KEYING_WINDOW = Duration.ofMinutes(15);

    /**
     * The resolver. Its unparseable-address line is written once per keying window, {@code app.audit.keying.window},
     * bound here directly so this package does not depend on the audit package (R-RL-020).
     */
    @Bean
    SourceKeyResolver sourceKeyResolver(ClientIpProperties properties, MeterRegistry meterRegistry, Clock clock,
            Environment environment) {
        Duration logWindow = Binder.get(environment).bind(KEYING_WINDOW, Duration.class)
                .orElse(DEFAULT_KEYING_WINDOW);
        return new SourceKeyResolver(properties, meterRegistry, clock, logWindow);
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> trustedProxyValve(ClientIpProperties properties) {
        return factory -> {
            if (properties.source() == Source.PROXY) {
                factory.addEngineValves(remoteIpValve(properties.trustedProxies()));
            }
        };
    }

    static RemoteIpValve remoteIpValve(List<String> trustedProxies) {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setRemoteIpHeader(FORWARDED_FOR);
        // Host masks (/32, /128): Tomcat matches them on bytes, so every spelling of an address matches.
        valve.setInternalProxies(trustedProxies.stream().map(ClientIpConfig::hostMask).collect(joining(",")));
        return valve;
    }

    private static String hostMask(String proxy) {
        InetAddress address = SourceKeyResolver.parseLiteral(proxy);
        return address.getHostAddress() + "/" + address.getAddress().length * 8;
    }
}
