package sg.securedhello.recovery;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.slf4j.LoggerFactory;

import sg.securedhello.audit.AuditEmitter;

/**
 * The runner's app-stopped interlock, on the audit side (ADR-072; T-RUN-010): the audit file appender is detached as
 * the runner's context starts and attached again only once its checks have passed. A runner started while the
 * application holds the database fails its context refresh on the H2 file lock, and so appends nothing to the audit
 * file, which is single-writer (no prudent mode). The stdout copy of each row is unaffected.
 *
 * <p>Logback is one per process, and the runner is one per process, so the detached appender is held statically.
 */
final class AuditFileGate {

    /** The dedicated audit file appender's name in {@code logback-spring.xml}. */
    static final String APPENDER = "AUDIT_FILE";

    private static Appender<ILoggingEvent> detached;

    private AuditFileGate() {
    }

    /**
     * Detaches the audit file appender.
     *
     * @throws IllegalStateException if the audit logger has no such appender, so the runner never runs unaudited
     */
    static synchronized void detach() {
        Logger audit = auditLogger();
        Appender<ILoggingEvent> appender = audit.getAppender(APPENDER);
        if (appender == null) {
            throw new IllegalStateException("The audit logger has no " + APPENDER + " appender; the runner will not"
                    + " run without its audit file");
        }
        audit.detachAppender(appender);
        detached = appender;
    }

    /** Attaches the audit file appender again, if {@link #detach} took it; otherwise does nothing. */
    static synchronized void attach() {
        if (detached != null) {
            auditLogger().addAppender(detached);
            detached = null;
        }
    }

    private static Logger auditLogger() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(AuditEmitter.AUDIT_LOGGER);
    }
}
