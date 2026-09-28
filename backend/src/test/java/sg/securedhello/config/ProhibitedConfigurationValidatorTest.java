package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import sg.securedhello.testsupport.Proves;

/** The refresh-phase prohibited-configuration validator, entry by entry (level U). */
class ProhibitedConfigurationValidatorTest {

    private static final String FILE_URL = "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=1000";

    @Test
    void aCleanConfigurationPassesInEveryPosture() {
        assertThat(ProhibitedConfigurationValidator.violations(production())).isEmpty();
        assertThat(ProhibitedConfigurationValidator.violations(dev())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/", ""})
    @Proves("T-CFG-024")
    void anyServletPathRefusesStartup(String value) {
        assertRefused(production().withProperty("spring.mvc.servlet.path", value), "spring.mvc.servlet.path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"framework", "FRAMEWORK", " framework "})
    @Proves("T-CFG-025")
    void theFrameworkForwardHeadersStrategyRefusesStartup(String value) {
        assertRefused(production().withProperty("server.forward-headers-strategy", value),
                "server.forward-headers-strategy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "none"})
    void otherForwardHeadersStrategiesAreNotThisValidatorsConcern(String value) {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("server.forward-headers-strategy", value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "3600", "8h"})
    @Proves("T-CFG-032")
    void aSessionCookieMaxAgeRefusesStartup(String value) {
        assertRefused(production().withProperty("server.servlet.session.cookie.max-age", value),
                "server.servlet.session.cookie.max-age");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "OFF"})
    @Proves("T-CFG-009")
    void theResetLinkLoggerSetInAConfigurationFileOutsideDevRefusesStartup(String level) {
        MockEnvironment environment = production();
        environment.getPropertySources().addFirst(configFile(ResetLinkLoggerGuard.LEVEL_PROPERTY, level));

        assertRefused(environment, ResetLinkLoggerGuard.LEVEL_PROPERTY);
    }

    @Test
    @Proves("T-CFG-009")
    void theResetLinkLoggerSetInAConfigurationFileUnderDevIsAllowed() {
        MockEnvironment environment = dev();
        environment.getPropertySources().addFirst(configFile(ResetLinkLoggerGuard.LEVEL_PROPERTY, "DEBUG"));

        assertThat(ProhibitedConfigurationValidator.violations(environment)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:testdb", "JDBC:H2:MEM:testdb", "jdbc:h2:tcp://localhost/mem:testdb", "", " "})
    @Proves("T-CFG-031")
    void anInMemoryOrBlankDatasourceRefusesStartupInEveryPosture(String url) {
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @Test
    @Proves("T-CFG-031")
    void anUnsetDatasourceRefusesStartup() {
        assertRefused(new MockEnvironment(), "spring.datasource.url");
    }

    @Test
    @Proves("T-CFG-031")
    void underDevOnlyAnH2FileDatasourceIsAllowed() {
        assertRefused(dev().withProperty("spring.datasource.url", "jdbc:postgresql://db/app"), "spring.datasource.url");
    }

    @Test
    @Proves("T-CFG-031")
    void outsideDevANonH2DatasourceIsNotRefused() {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("spring.datasource.url", "jdbc:postgresql://db/app"))).isEmpty();
    }

    @Test
    void everyViolationIsReportedTogetherAndNoValueIsEchoed() {
        MockEnvironment environment = production()
                .withProperty("spring.mvc.servlet.path", "/secret-looking-value")
                .withProperty("server.servlet.session.cookie.max-age", "31337");
        ProhibitedConfigurationValidator validator = new ProhibitedConfigurationValidator();
        validator.setEnvironment(environment);

        assertThatIllegalStateException()
                .isThrownBy(() -> validator.postProcessBeanFactory(new DefaultListableBeanFactory()))
                .withMessageContaining("spring.mvc.servlet.path")
                .withMessageContaining("server.servlet.session.cookie.max-age")
                .withMessageNotContaining("/secret-looking-value")
                .withMessageNotContaining("31337");
    }

    private static void assertRefused(MockEnvironment environment, String property) {
        assertThat(ProhibitedConfigurationValidator.violations(environment))
                .singleElement().asString().startsWith(property);
    }

    private static MockEnvironment production() {
        return new MockEnvironment().withProperty("spring.datasource.url", FILE_URL);
    }

    private static MockEnvironment dev() {
        MockEnvironment environment = production();
        environment.setActiveProfiles("dev");
        return environment;
    }

    /** A property source of the kind Boot loads from {@code application*.yml}. */
    private static OriginTrackedMapPropertySource configFile(String property, String value) {
        return new OriginTrackedMapPropertySource("Config resource 'class path resource [application-prod.yml]'",
                Map.of(property, value));
    }
}
