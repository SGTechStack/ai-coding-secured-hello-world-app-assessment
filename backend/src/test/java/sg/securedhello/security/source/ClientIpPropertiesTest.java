package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import sg.securedhello.security.source.ClientIpProperties.Source;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;

/** {@code app.security.client-ip.*} binds with safe defaults and refuses bad values at refresh (ADR-020; R-RL-007). */
class ClientIpPropertiesTest extends CtxNondevTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClientIpConfig.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    @Proves({"T-RL-027", "T-CFG-001"})
    void defaultsToTheSocketPeerAndASlash64() {
        runner.run(started(properties -> {
            assertThat(properties.source()).isEqualTo(Source.SOCKET);
            assertThat(properties.ipv6PrefixLength()).isEqualTo(64);
            assertThat(properties.trustedProxies()).isEmpty();
        }));
    }

    @Test
    @Proves({"T-RL-027", "T-CFG-001"})
    void productionConfigurationKeepsTheDefaults() {
        ClientIpProperties production = productionProperty("app.security.client-ip", ClientIpProperties.class);

        assertThat(production.source()).isEqualTo(Source.SOCKET);
        assertThat(production.ipv6PrefixLength()).isEqualTo(64);
        assertThat(production.trustedProxies()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {48, 128})
    @Proves("T-RL-027")
    void theBoundaryPrefixLengthsStart(int prefix) {
        runner.withPropertyValues("app.security.client-ip.ipv6-prefix-length=" + prefix)
                .run(started(properties -> assertThat(properties.ipv6PrefixLength()).isEqualTo(prefix)));
    }

    @ParameterizedTest
    @ValueSource(ints = {47, 129, 0, -64})
    @Proves("T-RL-027")
    void aPrefixLengthOutsideFortyEightToOneTwentyEightStopsStartup(int prefix) {
        runner.withPropertyValues("app.security.client-ip.ipv6-prefix-length=" + prefix)
                .run(refused("ipv6PrefixLength"));
    }

    @Test
    @Proves("T-CFG-001")
    void proxySourceWithNoTrustedProxiesStopsStartup() {
        runner.withPropertyValues("app.security.client-ip.source=proxy").run(refused("proxySourceNamed"));
    }

    @Test
    @Proves("T-CFG-001")
    void proxySourceWithAnEmptyTrustedProxiesListStopsStartup() {
        runner.withPropertyValues("app.security.client-ip.source=proxy", "app.security.client-ip.trusted-proxies=")
                .run(refused("proxySourceNamed"));
    }

    @Test
    void proxySourceWithNamedProxiesStarts() {
        runner.withPropertyValues("app.security.client-ip.source=proxy",
                        "app.security.client-ip.trusted-proxies=192.0.2.10,2001:db8::10")
                .run(started(properties -> {
                    assertThat(properties.source()).isEqualTo(Source.PROXY);
                    assertThat(properties.trustedProxies()).containsExactly("192.0.2.10", "2001:db8::10");
                }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"proxy.internal", "10.0.0.0/8", "10[.]0[.].*"})
    void aTrustedProxyThatIsNotAnIpLiteralStopsStartup(String proxy) {
        runner.withPropertyValues("app.security.client-ip.source=proxy",
                        "app.security.client-ip.trusted-proxies=192.0.2.10," + proxy)
                .run(refused("trustedProxiesLiteral"));
    }

    private static ContextConsumer<AssertableApplicationContext> started(
            java.util.function.Consumer<ClientIpProperties> assertions) {
        return context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SourceKeyResolver.class);
            assertions.accept(context.getBean(ClientIpProperties.class));
        };
    }

    private static ContextConsumer<AssertableApplicationContext> refused(String field) {
        return context -> assertThat(context).getFailure().rootCause().hasMessageContaining(field);
    }
}
