package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import sg.securedhello.testsupport.PortSession;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * A caller cannot force sampling (TM-02; ADR-063; R-OBS-015): with the sampling probability at 0.0, a request whose
 * {@code traceparent} carries the sampled flag {@code -01} still gets an unsampled span, because the inbound trace
 * context is dropped at the boundary and the probability sampler decides alone. Its own boot ({@code restart}), since
 * the probability is overridden for this case only.
 */
class ForcedSamplingTest {

    private static final String INBOUND_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    @Proves("T-OBS-004")
    void aSampledFlagFromTheCallerDoesNotSampleTheSpan() {
        Boot boot = RestartHarness.run(builder -> builder.profiles("dev").sources(SpanProbe.class), context -> {
            PortSession.client(context).get().uri("/actuator/health")
                    .header("traceparent", "00-" + INBOUND_TRACE_ID + "-00f067aa0ba902b7-01")
                    .exchange().expectStatus().isOk();

            Span seen = context.getBean(SpanProbe.class).span.get();
            assertThat(seen).as("the request's span").isNotNull();
            assertThat(seen.context().traceId()).isNotEqualTo(INBOUND_TRACE_ID);
            assertThat(seen.context().sampled()).as("sampled").isNotEqualTo(Boolean.TRUE);
        }, "--management.tracing.sampling.probability=0.0");

        assertThat(boot.failure()).isNull();
    }

    /** Records the span current inside the request, just after the observation filter opened it. */
    static class SpanProbe {

        final AtomicReference<Span> span = new AtomicReference<>();

        @Bean
        FilterRegistrationBean<Filter> spanProbeFilter(Tracer tracer) {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                span.set(tracer.currentSpan());
                chain.doFilter(request, response);
            });
            // After the trace restart filter and the observation filter, before Spring Security.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }
    }
}
