package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import io.opentelemetry.context.propagation.ContextPropagators;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.testsupport.Proves;

/** The header-stripping wrapper that restarts inbound traces (ADR-063). */
class TraceContextRestartFilterTest {

    @Test
    @Proves("T-OBS-015")
    void theDefaultPropagatorsWithBaggageOnReadOnlyHeadersTheFixedListStrips() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenTelemetrySdkAutoConfiguration.class,
                        MicrometerTracingAutoConfiguration.class, OpenTelemetryTracingAutoConfiguration.class))
                .withPropertyValues("management.tracing.baggage.enabled=true")
                .run(context -> {
                    List<String> fields = context.getBean(ContextPropagators.class).getTextMapPropagator().fields()
                            .stream().map(field -> field.toLowerCase(Locale.ROOT)).toList();
                    assertThat(fields).isNotEmpty().contains("traceparent", "baggage")
                            .isSubsetOf(TraceContextRestartFilter.STRIPPED_HEADERS.stream()
                                    .map(header -> header.toLowerCase(Locale.ROOT)).toList());
                });
    }

    @Test
    void everyTraceHeaderIsHiddenCaseInsensitivelyAndOthersPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        TraceContextRestartFilter.STRIPPED_HEADERS.forEach(name -> request.addHeader(name.toUpperCase(Locale.ROOT),
                "inbound"));
        request.addHeader("X-Vendor-Trace", "inbound");
        request.addHeader("Accept", "application/json");

        HttpServletRequest seen = filtered(new TraceContextRestartFilter(List.of("x-vendor-trace")), request);

        for (String name : TraceContextRestartFilter.STRIPPED_HEADERS) {
            assertThat(seen.getHeader(name)).as(name).isNull();
            assertThat(seen.getHeader(name.toLowerCase(Locale.ROOT))).as(name).isNull();
            assertThat(Collections.list(seen.getHeaders(name))).as(name).isEmpty();
            assertThat(seen.getIntHeader(name)).isEqualTo(-1);
            assertThat(seen.getDateHeader(name)).isEqualTo(-1);
        }
        assertThat(seen.getHeader("X-Vendor-Trace")).isNull();
        assertThat(Collections.list(seen.getHeaderNames())).containsExactly("Accept");
        assertThat(seen.getHeader("Accept")).isEqualTo("application/json");
        assertThat(Collections.list(seen.getHeaders("Accept"))).containsExactly("application/json");
    }

    @Test
    void numericAndDateHeadersOutsideTheStripSetPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.addHeader("Max-Forwards", "3");
        request.addHeader("If-Modified-Since", 1_000L);

        HttpServletRequest seen = filtered(new TraceContextRestartFilter(List.of()), request);

        assertThat(seen.getIntHeader("Max-Forwards")).isEqualTo(3);
        assertThat(seen.getDateHeader("If-Modified-Since")).isEqualTo(1_000L);
    }

    private static HttpServletRequest filtered(TraceContextRestartFilter filter, MockHttpServletRequest request)
            throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (downstream, response) -> seen.set((HttpServletRequest) downstream));
        return seen.get();
    }
}
