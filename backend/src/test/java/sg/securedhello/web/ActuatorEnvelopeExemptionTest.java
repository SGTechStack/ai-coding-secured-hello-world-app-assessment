package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * The Actuator health body is exempt from the error envelope, and only under {@code /actuator/**} (REJ-066).
 *
 * <p>This class deliberately starts its own context: it adds a health indicator that is always down, the only way to
 * see the 503 body without breaking a real dependency.
 */
@Import(ActuatorEnvelopeExemptionTest.AlwaysDown.class)
class ActuatorEnvelopeExemptionTest extends CtxDefaultTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class AlwaysDown {

        @Bean
        HealthIndicator alwaysDownHealthIndicator() {
            return () -> Health.down().withDetail("secret-detail", "never-shown").build();
        }
    }

    @Test
    @Proves("T-AUTH-017")
    void aDownHealthIs503WithActuatorsOwnBodyNotTheEnvelope() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/actuator/health")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(MediaType.parseMediaType(response.getContentType())
                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isFalse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"DOWN\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/api/health", "/api/actuator/health", "/actuatorx/health"})
    @Proves("T-AUTH-017")
    void aHealthLikePathOutsideTheActuatorBaseStillGetsTheEnvelope(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }
}
