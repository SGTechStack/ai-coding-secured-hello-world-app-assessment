package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WebEndpointsSupplier webEndpoints;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void healthIsTheOnlyExposedManagementEndpointAndNeverShowsDetails() throws Exception {
        assertThat(applicationContext.getBeanNamesForType(
                        Class.forName("io.micrometer.registry.otlp.OtlpMeterRegistry")))
                .isEmpty();
        assertThat(webEndpoints.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");
        MvcTestResult health = mvc.get().uri("/actuator/health").exchange();
        assertThat(health).hasStatusOk();
        String body = health.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("UP");
        assertThat(body).doesNotContain("\"components\"", "\"details\"");
    }

    @Test
    void realTraceparentCorrelatesOneSafePreSecurityRequestPair(CapturedOutput output) {
        assertThat(mvc.get()
                        .uri("/actuator/health?query-canary=secret")
                        .header("traceparent", TRACEPARENT)
                        .header("X-Correlation-ID", "custom-correlation-canary")
                        .header("Authorization", "Bearer auth-header-canary"))
                .hasStatusOk();

        String started = onlyLine(output, "Request received.");
        String completed = onlyLine(output, "Request completed.");
        assertThat(JsonPath.<String>read(started, "$.trace.id")).isEqualTo(TRACE_ID);
        assertThat(JsonPath.<String>read(completed, "$.trace.id")).isEqualTo(TRACE_ID);
        assertThat(JsonPath.<String>read(started, "$.span.id")).hasSize(16);
        assertThat(JsonPath.<String>read(started, "$.http.request.method")).isEqualTo("GET");
        assertThat(JsonPath.<String>read(started, "$.url.path")).isEqualTo("/actuator/health");
        assertThat(JsonPath.<String>read(completed, "$.http.request.method")).isEqualTo("GET");
        assertThat(JsonPath.<String>read(completed, "$.url.path")).isEqualTo("/actuator/health");
        assertThat(JsonPath.<Integer>read(completed, "$.http.response.status_code")).isEqualTo(200);
        assertThat(JsonPath.<String>read(completed, "$.event.outcome")).isEqualTo("success");
        assertThat(JsonPath.<Number>read(completed, "$.event.duration_ms").longValue()).isGreaterThanOrEqualTo(0);
        assertThat(JsonPath.<String>read(started, "$.service.name")).isEqualTo("eitri");
        assertThat(JsonPath.<String>read(started, "$.service.version")).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(JsonPath.<String>read(started, "$.service.environment")).isEqualTo("local");
        assertThat(OffsetDateTime.parse(JsonPath.read(started, "$['@timestamp']")).getOffset().toString())
                .isEqualTo("+08:00");
        assertThat(started + completed)
                .doesNotContain("query-canary", "custom-correlation-canary", "auth-header-canary", "correlation.id");
    }

    private static String onlyLine(CapturedOutput output, String message) {
        java.util.List<String> lines = output.getAll().lines()
                .filter(line -> line.contains("\"message\":\"" + message + "\""))
                .toList();
        assertThat(lines).as("structured lines for %s", message).hasSize(1);
        return lines.getFirst();
    }
}
