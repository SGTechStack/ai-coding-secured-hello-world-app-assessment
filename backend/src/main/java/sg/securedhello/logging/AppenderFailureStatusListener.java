package sg.securedhello.logging;

import java.util.concurrent.atomic.AtomicLong;

import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.stereotype.Component;

/**
 * Logging failure never breaks a request, and never passes unseen (LOG §5:375; LOG §5:376). Logback reports an
 * appender that cannot write, the audit file's included, as an ERROR status and carries on; this listener, registered
 * in {@code logback-spring.xml}, writes each such status to stderr and counts it on {@value #COUNTER}, which the
 * deployer's collector alerts on (R-OBS-004).
 *
 * <p>Logback creates the listener, not Spring, so the count is held here and published by the {@link Metrics} binder.
 */
public class AppenderFailureStatusListener implements StatusListener {

    /** The counter of logging failures: ERROR statuses Logback raised since the process started. */
    public static final String COUNTER = "logging.failures";

    private static final AtomicLong FAILURES = new AtomicLong();

    @Override
    public void addStatusEvent(Status status) {
        if (status.getLevel() >= Status.ERROR) {
            FAILURES.incrementAndGet();
            System.err.println("Logging failure: " + status);
        }
    }

    /** The failures counted so far. */
    public static long failures() {
        return FAILURES.get();
    }

    /** Publishes {@value #COUNTER}. */
    @Component
    public static final class Metrics implements MeterBinder {

        @Override
        public void bindTo(MeterRegistry registry) {
            FunctionCounter.builder(COUNTER, FAILURES, AtomicLong::get)
                    .description("Logback error statuses, such as an appender that stopped writing; an outage can "
                            + "report once, so alert on any increase, not on its size")
                    .register(registry);
        }
    }
}
