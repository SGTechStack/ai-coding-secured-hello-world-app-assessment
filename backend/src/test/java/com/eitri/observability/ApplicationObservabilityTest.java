package com.eitri.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.env.MockEnvironment;

class ApplicationObservabilityTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> capture;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(ApplicationObservability.class);
        capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(capture);
        capture.stop();
    }

    @Test
    void logsSafeReadyMetadataAndOneSuccessfulTimedConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        Queue<Long> ticks = new ArrayDeque<>(List.of(4_000_000L, 13_000_000L));
        MockEnvironment environment = new MockEnvironment().withProperty("secret", "credential-canary");
        environment.setActiveProfiles("test");
        ApplicationObservability observability = new ApplicationObservability(
                dataSource, environment, () -> new HostMetadata("safe-host", "192.0.2.1"), ticks::remove);

        observability.onApplicationReady();
        observability.onApplicationReady();

        assertThat(capture.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Application ready.", "Database connection check succeeded.");
        assertThat(fields(capture.list.get(0)))
                .containsEntry("host.name", "safe-host")
                .containsEntry("host.ip", "192.0.2.1")
                .containsEntry("spring.profiles.active", List.of("test"));
        assertThat(fields(capture.list.get(1)))
                .containsEntry("event.outcome", "success")
                .containsEntry("event.duration_ms", 9L);
        assertThat(capture.list.toString()).doesNotContain("credential-canary");
        verify(dataSource).getConnection();
        verify(connection, never()).getMetaData();
    }

    @Test
    void sanitizesDatabaseConnectionFailureWithoutReadingConnectionMetadata() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException(
                "jdbc:secret://db-host/private-db user=credential-canary"));
        Queue<Long> ticks = new ArrayDeque<>(List.of(1_000_000L, 6_000_000L));
        ApplicationObservability observability = new ApplicationObservability(
                dataSource, new MockEnvironment(), () -> new HostMetadata("host", "ip"), ticks::remove);

        observability.onApplicationReady();

        ILoggingEvent failure = capture.list.get(1);
        assertThat(fields(failure))
                .containsEntry("event.outcome", "failure")
                .containsEntry("event.duration_ms", 5L)
                .containsEntry("error_code", 503)
                .containsEntry("error_category", "database")
                .containsEntry("error_follow_up_action", true);
        assertThat(failure.getThrowableProxy().getMessage()).isEqualTo("Database connection check failure");
        assertThat(failure.getThrowableProxy().getStackTraceElementProxyArray().toString())
                .doesNotContain("jdbc:secret", "db-host", "private-db", "credential-canary");
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
