package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.PropertyNames;
import sg.securedhello.testsupport.Proves;

/** Production values read from the shipped configuration, without refreshing a production context (ADR-067). */
class ProductionBindingsTest extends CtxNondevTest {

    @Test
    @Proves("T-SES-028")
    void productionSweepsExpiredSessionsEveryMinute() {
        assertThat(productionProperty("spring.session.jdbc.cleanup-cron", String.class)).isEqualTo("0 * * * * *");
    }

    @Test
    @Proves("T-CFG-011")
    void productionNeverExposesTheLoggersEndpointAndCapsEveryEndpointAtReadOnly() {
        assertThat(productionProperty("management.endpoints.web.exposure.include", String[].class))
                .containsExactly("health");
        assertThat(productionProperty("management.endpoints.jmx.exposure.include", String[].class))
                .containsExactly("health");
        assertThat(productionProperty("management.endpoints.access.default", String.class)).isEqualTo("none");
        assertThat(productionProperty("management.endpoints.access.max-permitted", String.class))
                .isEqualTo("read-only");
    }

    @Test
    @Proves("T-OBS-005")
    void productionPinsTheRequestThreadPoolAndPublishesTomcatsThreadMeters() {
        assertThat(productionProperty("server.tomcat.threads.max", Integer.class)).isEqualTo(200);
        assertThat(productionProperty("server.tomcat.mbeanregistry.enabled", Boolean.class)).isTrue();
    }

    @Test
    @Proves("T-CFG-015")
    void noProductionSourceSetsAHealthGroupAdditionalPath() {
        assertThat(PropertyNames.of(productionEnvironment())).isNotEmpty()
                .noneMatch(PropertyNames::isHealthGroupAdditionalPath);
    }
}
