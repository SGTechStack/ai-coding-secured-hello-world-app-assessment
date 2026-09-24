package com.example.helloauth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Narrow-seam coverage for {@link AuditLogger} (same ListAppender idiom as
 * {@code EmailServiceTests}): each call must emit exactly one INFO event on
 * the dedicated {@code audit} logger, carrying the event name and the
 * structured fields — the fields that become top-level JSON keys under
 * LogstashEncoder.
 */
class AuditLoggerTests {

    private final AuditLogger audit = new AuditLogger();
    private final Logger logger =
        (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void loginSuccessEmitsEventNameAndActor() {
        audit.loginSucceeded("alice");

        ILoggingEvent event = singleEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("login_success");
        assertThat(markerText(event))
            .contains("event=login_success")
            .contains("actor=alice");
    }

    @Test
    void loginFailureEmitsReason() {
        audit.loginFailed("alice", "bad_credentials");

        assertThat(markerText(singleEvent()))
            .contains("event=login_failure")
            .contains("actor=alice")
            .contains("reason=bad_credentials");
    }

    @Test
    void throttledLoginEmitsRemoteAddr() {
        audit.loginThrottled("alice", "10.0.0.7");

        assertThat(markerText(singleEvent()))
            .contains("event=login_failure")
            .contains("reason=ip_throttled")
            .contains("remote_addr=10.0.0.7");
    }

    @Test
    void accountLockedEmitsTargetAndLockedUntil() {
        Instant until = Instant.parse("2026-01-01T00:15:00Z");

        audit.accountLocked("alice", until);

        assertThat(markerText(singleEvent()))
            .contains("event=account_locked")
            .contains("target=alice")
            .contains("locked_until=2026-01-01T00:15:00Z");
    }

    @Test
    void resetRequestedAndCompletedEmitTheirFields() {
        audit.passwordResetRequested("alice@example.com");
        audit.passwordResetCompleted("alice");

        assertThat(markerText(appender.list.get(0)))
            .contains("event=password_reset_requested")
            .contains("email=alice@example.com");
        assertThat(markerText(appender.list.get(1)))
            .contains("event=password_reset_completed")
            .contains("target=alice");
    }

    @Test
    void adminActionsEmitActorTargetAndChange() {
        audit.adminStatusChanged("admin", "bob", false);
        audit.adminRoleChanged("admin", "bob", "ADMIN");
        audit.adminUserDeleted("admin", "bob");

        assertThat(markerText(appender.list.get(0)))
            .contains("event=admin_status_changed")
            .contains("actor=admin").contains("target=bob")
            .contains("enabled=false");
        assertThat(markerText(appender.list.get(1)))
            .contains("event=admin_role_changed")
            .contains("role=ADMIN");
        assertThat(markerText(appender.list.get(2)))
            .contains("event=admin_user_deleted")
            .contains("actor=admin").contains("target=bob");
    }

    // ------------------------------------------------------------------
    // Commit-awareness — an audit line must describe committed state
    // ------------------------------------------------------------------

    @Test
    void eventInsideATransactionEmitsOnlyAfterCommit() {
        // A real DataSource transaction — inside it the call registers a
        // synchronization instead of writing, so nothing reaches the log
        // until commit; afterCommit then emits the line.
        EmbeddedDatabase db = h2();
        try {
            TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(db));

            tx.executeWithoutResult(status -> {
                audit.loginSucceeded("alice");
                // Still nothing — the call registered a synchronization
                // instead of writing; a synchronous emit would have
                // appended already.
                assertThat(appender.list).isEmpty();
            });

            // afterCommit wrote the line once the transaction committed.
            assertThat(appender.list).singleElement();
        } finally {
            db.shutdown();
        }
    }

    @Test
    void eventInsideARolledBackTransactionIsNeverEmitted() {
        // The false-positive window this class exists to close: the mutation
        // rolls back, so the audit line claiming it happened must not exist.
        EmbeddedDatabase db = h2();
        try {
            TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(db));

            tx.executeWithoutResult(status -> {
                audit.loginSucceeded("alice");
                status.setRollbackOnly();
            });

            assertThat(appender.list).isEmpty();
        } finally {
            db.shutdown();
        }
    }

    private static EmbeddedDatabase h2() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    }

    private ILoggingEvent singleEvent() {
        assertThat(appender.list).singleElement()
            .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.INFO));
        return appender.list.get(0);
    }

    /** The LogstashMarker's rendered entries — the future JSON fields. */
    private static String markerText(ILoggingEvent event) {
        assertThat(event.getMarkerList()).isNotEmpty();
        return event.getMarkerList().toString();
    }
}
