package sg.securedhello.web;

import java.util.List;

import jakarta.servlet.DispatcherType;

import io.opentelemetry.context.propagation.ContextPropagators;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link TraceContextRestartFilter} at {@link Ordered#HIGHEST_PRECEDENCE}, one ahead of Boot's
 * {@code ServerHttpObservationFilter}, which fixes the trace before Spring Security runs (ADR-063). It covers every
 * dispatch the observation filter does, plus the error dispatch.
 */
@Configuration(proxyBeanMethods = false)
public class TracingConfig {

    @Bean
    FilterRegistrationBean<TraceContextRestartFilter> traceContextRestartFilter(
            ObjectProvider<ContextPropagators> propagators) {
        ContextPropagators configured = propagators.getIfAvailable();
        FilterRegistrationBean<TraceContextRestartFilter> registration = new FilterRegistrationBean<>(
                new TraceContextRestartFilter(configured == null ? List.of()
                        : configured.getTextMapPropagator().fields()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
