package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/** ADR-061: the only reachable actuator endpoint is a detail-free {@code health}. */
class ActuatorExposureTest extends CtxDefaultTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    @Proves("T-OBS-009")
    void anonymousHealthIsUpWithNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/db", "/actuator/health/ping", "/actuator/health/diskSpace",
            "/actuator/health/liveness", "/actuator/health/readiness"})
    @Proves("T-OBS-009")
    void healthSubpathsAreNotFoundBecauseDetailsAreNeverShown(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/info", "/actuator/env", "/actuator/beans", "/actuator/metrics",
            "/actuator/prometheus", "/actuator/configprops", "/actuator/loggers", "/actuator/heapdump",
            "/actuator/threaddump", "/actuator/mappings", "/actuator/sessions", "/actuator/unknown"})
    @Proves("T-OBS-010")
    void everyOtherActuatorPathIsRefusedAnonymously(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/info", "/actuator/env", "/actuator/metrics"})
    @WithMockUser(roles = "ADMIN")
    @Proves("T-OBS-010")
    void everyOtherActuatorPathIsRefusedEvenToAnAdmin(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    @Proves("T-OBS-010")
    void shutdownIsRefused() throws Exception {
        mockMvc.perform(post("/actuator/shutdown")).andExpect(status().isForbidden());
    }

    @Test
    @Proves("T-OBS-011")
    void managementSecurityAutoConfigurationBacksOff() {
        var outcomes = ConditionEvaluationReport.get(context.getBeanFactory())
                .getConditionAndOutcomesBySource()
                .get(ManagementWebSecurityAutoConfiguration.class.getName());

        assertThat(outcomes).as("the auto-configuration was evaluated").isNotNull();
        assertThat(outcomes.isFullMatch()).as("and backed off").isFalse();
        assertThat(context.getBeanNamesForType(ManagementWebSecurityAutoConfiguration.class)).isEmpty();
    }
}
