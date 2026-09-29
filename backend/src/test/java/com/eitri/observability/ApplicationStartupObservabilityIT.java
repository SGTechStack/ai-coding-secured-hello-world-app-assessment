package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.EitriApplication;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest(
        classes = {EitriApplication.class, ApplicationStartupObservabilityIT.DeterministicObservability.class},
        properties = "observability.startup-test-context=true")
@ExtendWith(OutputCaptureExtension.class)
class ApplicationStartupObservabilityIT {

    @Test
    void readyContextEmitsOneSafeStartupAndDatabaseEventWithServiceMetadata(CapturedOutput output) {
        String startup = onlyLine(output, "Application ready.");
        String database = onlyLine(output, "Database connection check succeeded.");

        assertThat(JsonPath.<String>read(startup, "$.host.name")).isEqualTo("test-host");
        assertThat(JsonPath.<String>read(startup, "$.host.ip")).isEqualTo("192.0.2.20");
        assertThat(JsonPath.<List<String>>read(startup, "$.spring.profiles.active")).containsExactly("test");
        assertThat(JsonPath.<String>read(startup, "$.service.name")).isEqualTo("eitri");
        assertThat(JsonPath.<String>read(startup, "$.service.version")).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(JsonPath.<String>read(startup, "$.service.environment")).isEqualTo("local");
        assertThat(JsonPath.<String>read(database, "$.event.outcome")).isEqualTo("success");
        assertThat(JsonPath.<Number>read(database, "$.event.duration_ms").longValue()).isEqualTo(7L);
        assertThat(startup + database).doesNotContain("password", "credential", "jdbc:");
    }

    private static String onlyLine(CapturedOutput output, String message) {
        List<String> lines = output.getAll().lines()
                .filter(line -> line.contains("\"message\":\"" + message + "\""))
                .toList();
        assertThat(lines).as("startup lines for %s", message).hasSize(1);
        return lines.getFirst();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicObservability {

        @Bean
        @Primary
        HostMetadataResolver deterministicHostMetadataResolver() {
            return () -> new HostMetadata("test-host", "192.0.2.20");
        }

        @Bean
        @Primary
        Ticker deterministicMonotonicTicker() {
            AtomicLong ticks = new AtomicLong(1_000_000L);
            return () -> ticks.getAndAdd(7_000_000L);
        }
    }
}
