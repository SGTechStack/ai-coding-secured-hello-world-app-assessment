package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import java.util.Map;

import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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
    @ValueSource(strings = {"native", "NATIVE", " native ", "anything-else"})
    @Proves("T-CFG-026")
    void anyForwardHeadersStrategyButNoneRefusesStartup(String value) {
        assertRefused(production().withProperty("server.forward-headers-strategy", value),
                "server.forward-headers-strategy");
        assertRefused(dev().withProperty("server.forward-headers-strategy", value),
                "server.forward-headers-strategy");
    }

    @Test
    @Proves("T-CFG-026")
    void anUnsetForwardHeadersStrategyRefusesStartupBecauseCloudDetectionWouldPickOne() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", FILE_URL);

        assertRefused(environment, "server.forward-headers-strategy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "NONE", " none "})
    void theNoneForwardHeadersStrategyIsAllowed(String value) {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("server.forward-headers-strategy", value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.tomcat.remoteip.internal-proxies", "server.tomcat.remoteip.trusted-proxies",
            "server.tomcat.remoteip.remote-ip-header", "server.tomcat.remoteip.protocol-header",
            "server.tomcat.remoteip.port-header"})
    @Proves("T-CFG-026")
    void anyDirectTomcatRemoteIpPropertyRefusesStartup(String property) {
        assertRefused(production().withProperty(property, "anything"), "server.tomcat.remoteip.*");
        assertRefused(dev().withProperty(property, "anything"), "server.tomcat.remoteip.*");
    }

    @Test
    @Proves("T-CFG-026")
    void aTomcatRemoteIpPropertyFromTheEnvironmentIsRefusedInItsRelaxedSpelling() {
        MockEnvironment environment = production();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES", ".*")));

        assertRefused(environment, "server.tomcat.remoteip.*");
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
    @ValueSource(strings = {"jdbc:h2:mem:testdb", "JDBC:H2:MEM:testdb", "jdbc:h2:tcp://localhost/mem:testdb", "", " ",
            "jdbc:h2:memFS:x;LOCK_TIMEOUT=1000", "jdbc:h2:file:memFS:x;LOCK_TIMEOUT=1000",
            "jdbc:h2:file:nioMemLZF:x;LOCK_TIMEOUT=1000", "jdbc:h2:file:memLZF:x;LOCK_TIMEOUT=1000",
            "jdbc:h2:.;LOCK_TIMEOUT=1000", "jdbc:h2:./data/x;LOCK_TIMEOUT=1000",
            "jdbc:h2:tcp://db/./data/x;LOCK_TIMEOUT=1000"})
    @Proves({"T-CFG-031", "T-CFG-041"})
    void anInMemoryBlankOrNonFileH2DatasourceRefusesStartupInEveryPosture(String url) {
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @Test
    @Proves("T-CFG-031")
    void anUnsetDatasourceRefusesStartup() {
        assertRefused(new MockEnvironment().withProperty("server.forward-headers-strategy", "none"),
                "spring.datasource.url");
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

    @ParameterizedTest
    @ValueSource(strings = {";FILE_LOCK=NO", ";file_lock=no", "; FILE_LOCK = No ", ";FILE_LOCK=NO;LOCK_TIMEOUT=1000"})
    @Proves("T-CFG-013")
    void fileLockNoRefusesStartupInEveryPosture(String settings) {
        String url = FILE_URL + settings;
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @ParameterizedTest
    @ValueSource(strings = {";AUTO_SERVER=TRUE", ";auto_server=true", "; AUTO_SERVER = True ", ";AUTO_SERVER=1",
            ";AUTO_SERVER=YES", ";AUTO_SERVER=y", ";AUTO_SERVER=T", ";AUTO_SERVER=on",
            ";AUTO_SERVER=FALSE;AUTO_SERVER=TRUE"})
    @Proves("T-CFG-012")
    void autoServerRefusesStartupInEveryPosture(String settings) {
        String url = FILE_URL + settings;
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:file:./data/secured-hello\\;AUTO_SERVER=TRUE;LOCK_TIMEOUT=1000",
            "jdbc:h2:file:./data/secured-hello\\;FILE_LOCK=NO;LOCK_TIMEOUT=1000"})
    @Proves({"T-CFG-012", "T-CFG-013", "T-CFG-041"})
    void aBackslashBeforeTheFirstSemicolonDoesNotHideASettingBecauseH2EndsTheNameThere(String url) {
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @ParameterizedTest
    @ValueSource(strings = {";MODE=REGULAR\\;SET LOCK_TIMEOUT 0", ";SCHEMA=PUBLIC\\;CALL 1",
            ";CACHE_SIZE=8192\\\\", ";AUTO_SERVER=TR\\UE", ";AUTO\\_SERVER=TRUE", ";FILE_LOCK=N\\O"})
    @Proves({"T-CFG-012", "T-CFG-013", "T-CFG-041"})
    void aBackslashInTheSettingsRefusesStartupBecauseAnEscapedSemicolonRunsAsSql(String settings) {
        for (MockEnvironment environment : List.of(production(), dev())) {
            assertThat(ProhibitedConfigurationValidator.violations(
                    environment.withProperty("spring.datasource.url", FILE_URL + settings)))
                    .anySatisfy(violation -> assertThat(violation).contains("backslash"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {";INIT=SET LOCK_TIMEOUT 0", "; init = RUNSCRIPT FROM 'x.sql'"})
    @Proves("T-CFG-041")
    void anInitSettingRefusesStartupInEveryPosture(String settings) {
        assertRefused(production().withProperty("spring.datasource.url", FILE_URL + settings), "spring.datasource.url");
        assertRefused(dev().withProperty("spring.datasource.url", FILE_URL + settings), "spring.datasource.url");
    }

    @ParameterizedTest
    @ValueSource(strings = {";FILE_LOCK=FILE", ";FILE_LOCK=FS", ";AUTO_SERVER=FALSE", ";AUTO_SERVER=0",
            ";AUTO_SERVER=no", ";AUTO_SERVER=F", ";AUTO_SERVER=N"})
    void otherLockAndServerSettingsAreAllowed(String settings) {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("spring.datasource.url", FILE_URL + settings))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:file:./data/secured-hello", "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=50",
            "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=10000", "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=",
            "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=1000;LOCK_TIMEOUT=50"})
    @Proves("T-LCK-023")
    void outsideDevAnH2UrlMustPinLockTimeoutAtOneThousand(String url) {
        assertRefused(production().withProperty("spring.datasource.url", url), "spring.datasource.url");
    }

    @Test
    @Proves("T-LCK-023")
    void underDevTheHarnessMayShortenTheLockTimeout() {
        assertThat(ProhibitedConfigurationValidator.violations(dev().withProperty("spring.datasource.url",
                "jdbc:h2:file:./data/secured-hello;LOCK_TIMEOUT=50"))).isEmpty();
    }

    @Test
    @Proves("T-LCK-023")
    void aLockTimeoutPinnedWithSpacesOrLowerCaseIsAccepted() {
        assertThat(ProhibitedConfigurationValidator.violations(production().withProperty("spring.datasource.url",
                "jdbc:h2:file:./data/secured-hello; lock_timeout = 1000 "))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.flyway.url", "spring.flyway.init-sqls[0]", "spring.sql.init.mode",
            "spring.sql.init.schema-locations"})
    @Proves({"T-CFG-012", "T-CFG-013", "T-CFG-040"})
    void settingsThatReachTheDatabasePastTheUrlRulesRefuseStartup(String property) {
        String prefix = ProhibitedConfigurationValidator.DATASOURCE_BYPASS_PREFIXES.stream()
                .filter(property::startsWith).findFirst().orElseThrow();

        assertRefused(production().withProperty(property, "anything"), prefix);
        assertRefused(dev().withProperty(property, "anything"), prefix);
    }

    @ParameterizedTest
    @CsvSource({"spring.datasource.hikari.jdbc-url, jdbc-url",
            "spring.datasource.hikari.data-source-properties.FILE_LOCK, data-source-properties",
            "spring.datasource.hikari.data-source-properties.AUTO_SERVER, data-source-properties",
            "spring.datasource.hikari.connection-init-sql, connection-init-sql",
            "spring.datasource.hikari.connection-test-query, connection-test-query",
            "spring.datasource.hikari.exception-override-class-name, exception-override-class-name",
            "spring.datasource.hikari.data-source-class-name, data-source-class-name"})
    @Proves({"T-CFG-012", "T-CFG-013", "T-CFG-040"})
    void anyHikariSettingOutsideTheAllowlistRefusesStartup(String property, String setting) {
        assertRefused(production().withProperty(property, "anything"), "spring.datasource.hikari." + setting + " ");
        assertRefused(dev().withProperty(property, "anything"), "spring.datasource.hikari." + setting + " ");
    }

    @Test
    @Proves("T-CFG-040")
    void aHikariSettingFromTheEnvironmentIsJudgedInItsRelaxedSpelling() {
        MockEnvironment environment = production();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_DATASOURCE_HIKARI_CONNECTIONTESTQUERY", "SET LOCK_TIMEOUT 0",
                        "SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE", "5")));

        assertRefused(environment, "spring.datasource.hikari.connectiontestquery ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maximum-pool-size", "minimum-idle", "connection-timeout", "idle-timeout", "max-lifetime",
            "keepalive-time", "validation-timeout", "leak-detection-threshold", "pool-name"})
    @Proves("T-CFG-040")
    void theHikariPoolSizingTimeoutAndNameSettingsAreAllowed(String setting) {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("spring.datasource.hikari." + setting, "5"))).isEmpty();
    }

    @Test
    @Proves("T-CFG-014")
    void anH2ServerBeanRefusesStartup() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("h2TcpServer",
                BeanDefinitionBuilder.genericBeanDefinition(Server.class).getBeanDefinition());

        assertThatIllegalStateException().isThrownBy(() -> validator().postProcessBeanFactory(beanFactory))
                .withMessageContaining("h2TcpServer")
                .withMessageContaining(Server.class.getName());
    }

    @Test
    @Proves("T-CFG-014")
    void anH2ServerFromAFactoryMethodRefusesStartupWithoutBeingCreated() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("h2Servers",
                BeanDefinitionBuilder.genericBeanDefinition(H2Servers.class).getBeanDefinition());
        beanFactory.registerBeanDefinition("h2TcpServer", BeanDefinitionBuilder.genericBeanDefinition()
                .setFactoryMethodOnBean("tcpServer", "h2Servers").getBeanDefinition());

        assertThatIllegalStateException().isThrownBy(() -> validator().postProcessBeanFactory(beanFactory))
                .withMessageContaining("h2TcpServer");
        assertThat(H2Servers.created).as("no server was created to learn its type").isZero();
    }

    @Test
    void aBeanFactoryWithNoH2ServerPasses() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("somethingElse",
                BeanDefinitionBuilder.genericBeanDefinition(Object.class).getBeanDefinition());

        assertThatNoException().isThrownBy(() -> validator().postProcessBeanFactory(beanFactory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.data.redis.host", "spring.session.redis.namespace",
            "spring.session.hazelcast.map-name", "spring.session.mongodb.collection-name", "spring.hazelcast.config",
            "bucket4j.enabled", "bucket4j.filters[0].url"})
    @Proves("T-CFG-029")
    void anyClusteringPropertyRefusesStartup(String property) {
        String prefix = ProhibitedConfigurationValidator.CLUSTERING_PREFIXES.stream()
                .filter(property::startsWith).findFirst().orElseThrow();

        assertRefused(production().withProperty(property, "anything"), prefix + ".*");
        assertRefused(dev().withProperty(property, "anything"), prefix + ".*");
    }

    @Test
    @Proves("T-CFG-029")
    void aClusteringPropertyFromTheEnvironmentIsRefusedInItsRelaxedSpelling() {
        MockEnvironment environment = production();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_DATA_REDIS_HOST", "cache.internal")));

        assertRefused(environment, "spring.data.redis.*");
    }

    @Test
    void aPropertyThatOnlySharesAPrefixIsNotClustering() {
        assertThat(ProhibitedConfigurationValidator.violations(
                production().withProperty("spring.data.redistributed", "x"))).isEmpty();
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

    /** The committed production values the validator reads, as {@code application.yml} sets them. */
    private static MockEnvironment production() {
        return new MockEnvironment().withProperty("spring.datasource.url", FILE_URL)
                .withProperty("server.forward-headers-strategy", "none");
    }

    private static ProhibitedConfigurationValidator validator() {
        ProhibitedConfigurationValidator validator = new ProhibitedConfigurationValidator();
        validator.setEnvironment(production());
        return validator;
    }

    /** A configuration class whose factory method returns an H2 server, as a {@code @Bean} method would. */
    static class H2Servers {

        static int created;

        Server tcpServer() {
            created++;
            return new Server();
        }
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
