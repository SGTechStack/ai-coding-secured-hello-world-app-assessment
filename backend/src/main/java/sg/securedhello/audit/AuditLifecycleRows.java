package sg.securedhello.audit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.config.ResetLinkLoggerGuard;
import sg.securedhello.security.source.ClientIpProperties;

/**
 * Emits the lifecycle rows: startup once the application is ready (row 43), shutdown as the context closes (row 44).
 * Also closes the keying window on its schedule and at shutdown (ADR-019).
 */
class AuditLifecycleRows {

    /** The loggers whose effective level the startup row records. */
    static final List<String> AUDIT_RELEVANT_LOGGERS = List.of(AuditEmitter.AUDIT_LOGGER,
            ResetLinkLoggerGuard.LOGGER_NAME);

    private final AuditEmitter emitter;
    private final Environment environment;
    private final ClientIpProperties clientIp;
    private final ApplicationKeys keys;
    private final LoggingSystem loggingSystem;

    AuditLifecycleRows(AuditEmitter emitter, Environment environment, ClientIpProperties clientIp,
            ApplicationKeys keys, LoggingSystem loggingSystem) {
        this.emitter = emitter;
        this.environment = environment;
        this.clientIp = clientIp;
        this.keys = keys;
        this.loggingSystem = loggingSystem;
    }

    @EventListener(ApplicationReadyEvent.class)
    void started() {
        InetAddress host = localHost();
        String[] profiles = environment.getActiveProfiles();
        emitter.emit(AuditEvent.APPLICATION_STARTUP, new StartupContext(
                host == null ? "unknown" : host.getHostName(),
                host == null ? List.of() : List.of(host.getHostAddress()),
                profiles.length == 0 ? List.of("default") : Arrays.asList(profiles),
                clientIp.ipv6PrefixLength(),
                keys.all().stream().map(key -> key.property() + "=" + key.fingerprint()).toList(),
                AUDIT_RELEVANT_LOGGERS.stream().map(name -> name + "=" + effectiveLevel(name)).toList()));
    }

    /** Writes the keyed rows still held, so none is lost, then the shutdown row. */
    @EventListener(ContextClosedEvent.class)
    void stopping() {
        emitter.closeKeyingWindow();
        emitter.emit(AuditEvent.APPLICATION_SHUTDOWN, AuditContext.NONE);
    }

    /**
     * Closes a keying window that has run its length when no keyed occurrence arrives to close it, so a quiet period
     * after an attack does not hold its rows back. The window itself is measured on the injected {@code Clock}.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void closeKeyingWindowIfDue() {
        emitter.closeKeyingWindowIfDue();
    }

    /** The level {@code loggerName} would log at: its own, or its nearest configured ancestor's. */
    private String effectiveLevel(String loggerName) {
        for (String name = loggerName; ; name = name.substring(0, Math.max(0, name.lastIndexOf('.')))) {
            LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(
                    name.isEmpty() ? LoggingSystem.ROOT_LOGGER_NAME : name);
            if (configuration != null) {
                return String.valueOf(configuration.getEffectiveLevel());
            }
            if (name.isEmpty()) {
                return "UNKNOWN";
            }
        }
    }

    private static InetAddress localHost() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
