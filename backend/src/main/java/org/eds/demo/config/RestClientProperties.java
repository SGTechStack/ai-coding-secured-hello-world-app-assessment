package org.eds.demo.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Timeouts of the shared outbound {@code RestClient}, bound under {@code rest.client}.
 *
 * @param connectTimeout how long to wait for the connection to open
 * @param readTimeout how long to wait for the response once connected
 */
@Validated
@ConfigurationProperties(prefix = "rest.client")
public record RestClientProperties(
    @DefaultValue("30s") @DurationMin(nanos = 1) Duration connectTimeout,
    @DefaultValue("30s") @DurationMin(nanos = 1) Duration readTimeout) {}
