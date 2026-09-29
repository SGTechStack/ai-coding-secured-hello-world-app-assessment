package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.test.context.TestPropertySource;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * ADR-061: an actuator endpoint switched on by accident is refused by the explicit {@code EndpointRequest} rule, not
 * left to whatever rule comes later.
 *
 * <p>This class deliberately starts its own context. In the shipped configuration only {@code health} is exposed and
 * no matrix row can reach {@code /actuator}, so {@code anyRequest().denyAll()} refuses the same requests and a test
 * there cannot tell the two rules apart. Here the context exposes {@code info} by accident, moves the actuator under
 * {@code /api}, and replaces the whitelist with {@code GET /api/**}: only the explicit rule stands between an anonymous
 * caller and the endpoint.
 */
@TestPropertySource(properties = {
        "management.endpoints.web.base-path=/api/actuator",
        "management.endpoints.web.exposure.include=health,info",
        "management.endpoint.info.access=read-only",
        "app.security.authorization.whitelist[0].method=GET",
        "app.security.authorization.whitelist[0].path=/api/**"})
class ActuatorExplicitDenyTest extends CtxDefaultTest {

    @Autowired
    PathMappedEndpoints endpoints;

    @Test
    void theAccidentalEndpointReallyIsExposedAndHealthIsStillReachable() throws Exception {
        assertThat(endpoints.getPath(EndpointId.of("info"))).isEqualTo("/api/actuator/info");
        mockMvc.perform(get("/api/actuator/health")).andExpect(status().isOk());
        // The whitelist row is in effect: an unmapped route under it reaches MVC, whose 404 is rendered as
        // ACCESS_DENIED (ADR-031), not refused anonymously by the catch-all's 401.
        mockMvc.perform(get("/api/no-such-route")).andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/actuator", "/api/actuator/info"})
    @Proves("T-OBS-010")
    void anExposedEndpointAWhitelistRowWouldAdmitIsRefusedByTheExplicitRule(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
