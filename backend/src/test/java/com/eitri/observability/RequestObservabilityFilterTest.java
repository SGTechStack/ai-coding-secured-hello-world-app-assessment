package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestObservabilityFilterTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> capture;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(RequestObservabilityFilter.class);
        capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(capture);
        capture.stop();
        MDC.clear();
    }

    @Test
    void logsOnlySafeRequestMetadataAndMonotonicCompletion() throws Exception {
        Queue<Long> ticks = new ArrayDeque<>(java.util.List.of(1_000_000L, 8_000_000L));
        RequestObservabilityFilter filter = new RequestObservabilityFilter(ticks::remove);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("secret=query-canary");
        request.addHeader("Authorization", "Bearer header-canary");
        request.setRemoteAddr("203.0.113.10");
        request.setContent("body-canary".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("user.id", "existing-user");
        MDC.put("traceId", "existing-trace");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(403));

        assertThat(capture.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Request received.", "Request completed.");
        Map<String, Object> started = fields(capture.list.get(0));
        Map<String, Object> completed = fields(capture.list.get(1));
        assertThat(started)
                .containsEntry("event.category", java.util.List.of("network"))
                .containsEntry("event.type", java.util.List.of("start"))
                .containsEntry("http.request.method", "POST")
                .containsEntry("url.path", "/api/v1/auth/login");
        assertThat(completed)
                .containsEntry("event.type", java.util.List.of("end"))
                .containsEntry("event.outcome", "failure")
                .containsEntry("http.request.method", "POST")
                .containsEntry("url.path", "/api/v1/auth/login")
                .containsEntry("http.response.status_code", 403)
                .containsEntry("event.duration_ms", 7L);
        assertThat(started.toString() + completed)
                .doesNotContain("query-canary", "header-canary", "203.0.113.10", "body-canary");
        assertThat(MDC.get("user.id")).isEqualTo("existing-user");
        assertThat(MDC.get("traceId")).isEqualTo("existing-trace");
    }

    @Test
    void logsFailureAndRethrowsWhenTheChainEscapes() {
        Queue<Long> ticks = new ArrayDeque<>(java.util.List.of(0L, 2_000_000L));
        RequestObservabilityFilter filter = new RequestObservabilityFilter(ticks::remove);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    throw new ServletException("canary");
                }))
                .isInstanceOf(ServletException.class);

        assertThat(fields(capture.list.get(1)))
                .containsEntry("event.outcome", "failure")
                .containsEntry("http.response.status_code", 500)
                .containsEntry("event.duration_ms", 2L);
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
