package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityRealServerIT {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-b7ad6b7169203331-01";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void deniedRealRequestIsLoggedOnceOutsideSecurityWithTraceOnlyCorrelation(CapturedOutput output)
            throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/anything?query-canary=secret"))
                        .header("traceparent", TRACEPARENT)
                        .header("X-Correlation-ID", "custom-correlation-canary")
                        .header("Authorization", "Bearer auth-header-canary")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.headers().firstValue("X-Correlation-ID")).isEmpty();

        String started = onlyTraceLine(output, "Request received.");
        String completed = onlyTraceLine(output, "Request completed.");
        assertThat(JsonPath.<String>read(started, "$.trace.id")).isEqualTo(TRACE_ID);
        assertThat(JsonPath.<String>read(completed, "$.trace.id")).isEqualTo(TRACE_ID);
        assertThat(JsonPath.<String>read(started, "$.http.request.method")).isEqualTo("GET");
        assertThat(JsonPath.<String>read(completed, "$.http.request.method")).isEqualTo("GET");
        assertThat(JsonPath.<String>read(started, "$.url.path")).isEqualTo("/api/v1/anything");
        assertThat(JsonPath.<String>read(completed, "$.url.path")).isEqualTo("/api/v1/anything");
        assertThat(JsonPath.<Integer>read(completed, "$.http.response.status_code")).isEqualTo(401);
        assertThat(JsonPath.<String>read(completed, "$.event.outcome")).isEqualTo("failure");
        assertThat(started + completed)
                .doesNotContain("query-canary", "custom-correlation-canary", "auth-header-canary", "correlation.id");
    }

    private static String onlyTraceLine(CapturedOutput output, String message) {
        List<String> lines = output.getAll().lines()
                .filter(line -> line.contains("\"message\":\"" + message + "\""))
                .filter(line -> line.contains(TRACE_ID))
                .toList();
        assertThat(lines).as("real-server trace lines for %s", message).hasSize(1);
        return lines.getFirst();
    }
}
