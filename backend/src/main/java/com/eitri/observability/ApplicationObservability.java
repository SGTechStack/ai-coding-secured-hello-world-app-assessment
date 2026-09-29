package com.eitri.observability;

import com.eitri.logging.SanitizedLogException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Emits one safe application-ready event and one timed database connection outcome. */
@Component
public final class ApplicationObservability {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationObservability.class);

    private final DataSource dataSource;
    private final Environment environment;
    private final HostMetadataResolver hostResolver;
    private final Ticker ticker;
    private final AtomicBoolean logged = new AtomicBoolean();

    public ApplicationObservability(
            DataSource dataSource, Environment environment, HostMetadataResolver hostResolver, Ticker ticker) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.hostResolver = hostResolver;
        this.ticker = ticker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!logged.compareAndSet(false, true)) {
            return;
        }
        HostMetadata host = hostResolver.resolve();
        LOGGER.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("start"))
                .addKeyValue("event.action", "application-startup")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("host.name", host.name())
                .addKeyValue("host.ip", host.address())
                .addKeyValue("spring.profiles.active", Arrays.asList(environment.getActiveProfiles()))
                .setMessage("Application ready.")
                .log();
        logDatabaseConnectionOutcome();
    }

    private void logDatabaseConnectionOutcome() {
        long started = ticker.read();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                logDatabaseFailure(started, new IllegalStateException("Database validation failed"));
                return;
            }
            LOGGER.atInfo()
                    .addKeyValue("event.kind", "state")
                    .addKeyValue("event.category", List.of("database"))
                    .addKeyValue("event.type", List.of("connection"))
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.duration_ms", elapsedMillis(started))
                    .setMessage("Database connection check succeeded.")
                    .log();
        } catch (Exception failure) {
            logDatabaseFailure(started, failure);
        }
    }

    private void logDatabaseFailure(long started, Throwable failure) {
        LOGGER.atError()
                .setCause(SanitizedLogException.from(failure, "Database connection check failure"))
                .addKeyValue("event.kind", "state")
                .addKeyValue("event.category", List.of("database"))
                .addKeyValue("event.type", List.of("connection"))
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.duration_ms", elapsedMillis(started))
                .addKeyValue("error_code", 503)
                .addKeyValue("error_category", "database")
                .addKeyValue("error_follow_up_action", true)
                .setMessage("Database connection check failed.")
                .log();
    }

    private long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, ticker.read() - started));
    }
}
