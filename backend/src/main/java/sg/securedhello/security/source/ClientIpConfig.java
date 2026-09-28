package sg.securedhello.security.source;

import static java.util.stream.Collectors.joining;

import java.net.InetAddress;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    SourceKeyResolver sourceKeyResolver(ClientIpProperties properties, MeterRegistry meterRegistry) {
        return new SourceKeyResolver(properties, meterRegistry);
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
