package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LoginSecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "app.security.login.lockout-threshold=5",
                    "app.security.login.lockout-duration=15m",
                    "app.security.login.lockout-window=10m",
                    "app.security.login.ip-throttle-max-failures=20",
                    "app.security.login.ip-throttle-window=15m",
                    "app.security.login.ip-throttle-cache-bound=10000");

    @Test
    void validLoginSecurityConfigurationBindsToTypedProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            LoginSecurityProperties properties = context.getBean(LoginSecurityProperties.class);
            assertThat(properties.lockoutThreshold()).isEqualTo(5);
            assertThat(properties.lockoutDuration()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.lockoutWindow()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.ipThrottleMaxFailures()).isEqualTo(20);
            assertThat(properties.ipThrottleWindow()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.ipThrottleCacheBound()).isEqualTo(10000);
        });
    }

    @ParameterizedTest
    @CsvSource({
        "lockout-threshold, 0",
        "lockout-duration, 0s",
        "lockout-window, 0s",
        "ip-throttle-max-failures, 0",
        "ip-throttle-window, 0s",
        "ip-throttle-cache-bound, 0"
    })
    void nonPositiveValueStopsStartup(String name, String value) {
        contextRunner.withPropertyValues("app.security.login." + name + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("app.security.login." + name + " must be positive");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoginSecurityProperties.class)
    static class PropertiesConfiguration {}
}
