package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class ObservabilityConfigurationTest {

    @Test
    void requestLoggingRunsImmediatelyBeforeSpringSecurity() {
        SecurityFilterProperties security = new SecurityFilterProperties();
        security.setOrder(42);

        FilterRegistrationBean<RequestObservabilityFilter> registration =
                new ObservabilityConfiguration().requestObservabilityFilter(System::nanoTime, security);

        assertThat(registration.getOrder()).isEqualTo(41);
        assertThat(registration.getFilter()).isInstanceOf(RequestObservabilityFilter.class);
    }
}
