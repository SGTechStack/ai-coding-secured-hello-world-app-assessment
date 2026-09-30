package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static sg.securedhello.testsupport.CsrfSession.validToken;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.Arrays;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sg.securedhello.testsupport.PropertyNames;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/** Actuator guards that the running application, not only its configuration files, must show. */
class ActuatorGuardsTest extends CtxDefaultTest {

    private static final String CLOUD_FOUNDRY = "/cloudfoundryapplication";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @Proves("T-CFG-015")
    void noSourceOfTheRunningContextSetsAHealthGroupAdditionalPath() {
        assertThat(PropertyNames.isHealthGroupAdditionalPath(
                "management.endpoint.health.group.storage.additional-path")).as("the matcher itself").isTrue();
        assertThat(PropertyNames.of(environment)).isNotEmpty()
                .noneMatch(PropertyNames::isHealthGroupAdditionalPath);
        assertThat(environment.getProperty("management.endpoint.health.probes.add-additional-paths", Boolean.class,
                false)).isFalse();
    }

    @Test
    @Proves("T-CFG-016")
    void noHandlerAndNoCloudFoundryEndpointMappingServesTheCloudFoundryPath() {
        assertThat(environment.getProperty("server.servlet.context-path")).isNullOrEmpty();
        List<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()).toList();
        assertThat(patterns).isNotEmpty().noneMatch(pattern -> pattern.startsWith(CLOUD_FOUNDRY));
        assertThat(Arrays.stream(context.getBeanDefinitionNames()))
                .noneMatch(name -> name.toLowerCase().contains("cloudfoundry"));
    }

    @ParameterizedTest
    @ValueSource(strings = {CLOUD_FOUNDRY, CLOUD_FOUNDRY + "/", CLOUD_FOUNDRY + "/health", CLOUD_FOUNDRY + "/env"})
    @Proves("T-CFG-016")
    void theCloudFoundryPathIsAnUnmatchedRouteForEveryone(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get(path).with(user("cf-admin").roles("ADMIN"))).andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves("T-CFG-011")
    void aPostChangingALoggerLevelIsRefusedAndTheLevelIsUnchanged() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("sg.securedhello");
        Level before = logger.getLevel();

        mockMvc.perform(post("/actuator/loggers/sg.securedhello").with(validToken(mockMvc))
                        .with(user("loggers-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuredLevel\":\"TRACE\"}"))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));

        assertThat(logger.getLevel()).isEqualTo(before);
        assertThat(logger.isTraceEnabled()).isFalse();
    }
}
