package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound trace context is restarted at the boundary, through real Tomcat (ADR-063; TM-02). The application runs once
 * for the class on its own random port, with a probe filter behind Boot's observation filter that logs one line per
 * request; that line's {@code trace.id} is the one every log line of the request carries.
 *
 * <p>Baggage correlation is configured to a probe key, while the application's {@code baggage.enabled: false} is left
 * alone (T-AUD-012): a caller-set value must reach neither the MDC nor any log line.
 */
@ExtendWith(OutputCaptureExtension.class)
class TraceContextRestartTest {

    private static final String INBOUND_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + INBOUND_TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String BAGGAGE_KEY = "probe-key";
    private static final String BAGGAGE_VALUE = "caller-baggage-value-6d0b";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ConfigurableApplicationContext application;
    private static int port;

    @BeforeAll
    static void start() {
        application = new SpringApplicationBuilder(SecuredHelloApplication.class, TraceProbe.class)
                .profiles("dev")
                .initializers(new TemporaryH2FileInitializer())
                .run("--server.port=0", "--app.security.password.bcrypt-strength=4",
                        "--management.tracing.baggage.remote-fields=" + BAGGAGE_KEY,
                        "--management.tracing.baggage.correlation.fields=" + BAGGAGE_KEY);
        port = ((WebServerApplicationContext) application).getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        application.close();
    }

    static Stream<Arguments> inboundFormats() {
        return Stream.of(
                Arguments.of("W3C traceparent", Map.of("traceparent", TRACEPARENT)),
                Arguments.of("W3C traceparent and tracestate", Map.of("traceparent", TRACEPARENT,
                        "tracestate", "vendor=opaque")),
                Arguments.of("B3 single header", Map.of("b3", INBOUND_TRACE_ID + "-00f067aa0ba902b7-1")),
                Arguments.of("B3 multi header", Map.of("X-B3-TraceId", INBOUND_TRACE_ID,
                        "X-B3-SpanId", "00f067aa0ba902b7", "X-B3-Sampled", "1")),
                Arguments.of("mixed-case W3C", Map.of("TraceParent", TRACEPARENT)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inboundFormats")
    @Proves("T-OBS-002")
    void noInboundFormatSetsTheLoggedTraceId(String format, Map<String, String> headers, CapturedOutput output)
            throws Exception {
        Observed observed = send(headers);

        String logged = loggedTraceId(output, observed.probeId());
        assertThat(logged).matches("[0-9a-f]{32}").isNotEqualTo(INBOUND_TRACE_ID);
        assertThat(observed.envelopeTraceId()).as("the error envelope quotes the logged trace id").isEqualTo(logged);
        assertThat(output.getAll()).doesNotContain(INBOUND_TRACE_ID);
    }

    @Test
    @Proves("T-OBS-003")
    void twoRequestsPinnedToOneTraceparentGetDifferentTraceIds(CapturedOutput output) throws Exception {
        Observed first = send(Map.of("traceparent", TRACEPARENT));
        Observed second = send(Map.of("traceparent", TRACEPARENT));

        assertThat(loggedTraceId(output, first.probeId())).isNotEqualTo(loggedTraceId(output, second.probeId()));
    }

    @Test
    @Proves("T-AUD-012")
    void callerSetBaggageReachesNeitherTheMdcNorAnyLogLine(CapturedOutput output) throws Exception {
        Observed observed = send(Map.of("baggage", BAGGAGE_KEY + "=" + BAGGAGE_VALUE,
                BAGGAGE_KEY, BAGGAGE_VALUE));

        assertThat(observed.mdc()).isNotNull().doesNotContainKey(BAGGAGE_KEY).doesNotContainValue(BAGGAGE_VALUE);
        assertThat(observed.mdc()).containsKey("traceId");
        assertThat(output.getAll()).doesNotContain(BAGGAGE_VALUE);
    }

    /** What the probe saw of one request, and the envelope the unauthenticated request got. */
    private record Observed(String probeId, Map<String, String> mdc, String envelopeTraceId) {
    }

    private static Observed send(Map<String, String> headers) throws IOException, InterruptedException {
        String probeId = "probe-" + System.identityHashCode(headers) + "-" + Long.toHexString(System.nanoTime());
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/hello"))
                .header(TraceProbe.PROBE_HEADER, probeId);
        headers.forEach(request::header);
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request.build(), BodyHandlers.ofString());
        }
        assertThat(response.statusCode()).isEqualTo(401);
        String envelopeTraceId = JSON.readTree(response.body()).get("traceId").asString();
        return new Observed(probeId, application.getBean(TraceProbe.class).mdc.get(), envelopeTraceId);
    }

    private static String loggedTraceId(CapturedOutput output, String probeId) {
        return EcsJson.rows(output.getOut()).stream()
                .filter(row -> ("Trace probe " + probeId).equals(row.get("message")))
                .map(row -> (String) row.get("trace.id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no probe line for " + probeId));
    }

    /**
     * Logs one line per request from inside the request's trace, and records the MDC it saw. Not a
     * {@code @Configuration}, so component scanning never picks it up; it is registered only as an explicit source.
     */
    static class TraceProbe {

        static final String PROBE_HEADER = "X-Trace-Probe";
        private static final Logger log = LoggerFactory.getLogger(TraceProbe.class);

        final AtomicReference<Map<String, String>> mdc = new AtomicReference<>();

        @Bean
        FilterRegistrationBean<Filter> traceProbeFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                mdc.set(MDC.getCopyOfContextMap());
                log.info("Trace probe {}", ((jakarta.servlet.http.HttpServletRequest) request)
                        .getHeader(PROBE_HEADER));
                chain.doFilter(request, response);
            });
            // After the observation filter (HIGHEST_PRECEDENCE + 1), before Spring Security answers the 401.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }
    }
}
