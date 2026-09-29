package com.eitri.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class CustomStructuredLogEncoderTest {

    private LoggerContext context;
    private CustomStructuredLogEncoder encoder;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        encoder = new CustomStructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        context.stop();
    }

    @Test
    void nestsWorkaroundFieldsAndPreservesEcsThrowableFields() {
        LoggingEvent event = event(new SanitizedLogExceptionFactory().failure());
        event.addKeyValuePair(new KeyValuePair("error_code", 500));
        event.addKeyValuePair(new KeyValuePair("error_category", "application"));
        event.addKeyValuePair(new KeyValuePair("error_follow_up_action", true));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(JsonPath.<Integer>read(json, "$.error.code")).isEqualTo(500);
        assertThat(JsonPath.<String>read(json, "$.error.category")).isEqualTo("application");
        assertThat(JsonPath.<Boolean>read(json, "$.error.follow_up_action")).isTrue();
        assertThat(JsonPath.<String>read(json, "$.error.type"))
                .isEqualTo(SanitizedLogException.class.getName());
        assertThat(JsonPath.<String>read(json, "$.error.message")).isEqualTo("Safe failure");
        assertThat(JsonPath.<String>read(json, "$.error.stack_trace")).contains("SanitizedLogException");
        assertThat(json)
                .doesNotContain("error_code", "error_category", "error_follow_up_action", "secret-canary")
                .endsWith("\n");
    }

    @Test
    void autoResolvesCategoryWithoutOverridingExplicitCategory() {
        LoggingEvent automatic = event(new IllegalArgumentException("safe"));
        String automaticJson = new String(encoder.encode(automatic), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(automaticJson, "$.error.category")).isEqualTo("application");

        LoggingEvent explicit = event(new ConnectExceptionForTest());
        explicit.addKeyValuePair(new KeyValuePair("error_category", "custom"));
        String explicitJson = new String(encoder.encode(explicit), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(explicitJson, "$.error.category")).isEqualTo("custom");
    }

    private LoggingEvent event(Throwable failure) {
        Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                getClass().getName(), logger, Level.ERROR, "Static error.", failure, null);
        event.setMDCPropertyMap(java.util.Map.of());
        return event;
    }

    private static final class SanitizedLogExceptionFactory {
        SanitizedLogException failure() {
            return SanitizedLogException.from(new IllegalStateException("secret-canary"), "Safe failure");
        }
    }

    private static final class ConnectExceptionForTest extends java.net.ConnectException {}
}
