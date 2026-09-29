package com.eitri.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;

/** Captures events while encoding them through the logger's production appender. */
public final class StructuredLogTestCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> capture;
    private final Encoder<ILoggingEvent> encoder;

    private StructuredLogTestCapture(String loggerName, String appenderName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        OutputStreamAppender<ILoggingEvent> destination =
                (OutputStreamAppender<ILoggingEvent>) logger.getAppender(appenderName);
        if (destination == null) {
            throw new IllegalStateException("Appender not found: " + appenderName);
        }
        this.encoder = destination.getEncoder();
        this.capture = new ListAppender<>();
        this.capture.setName("TEST_CAPTURE");
        this.capture.start();
        this.logger.addAppender(capture);
    }

    public static StructuredLogTestCapture audit() {
        return new StructuredLogTestCapture("AUDIT", "AUDIT_FILE");
    }

    public String line(String message) {
        ILoggingEvent event = capture.list.stream()
                .filter(candidate -> message.equals(candidate.getFormattedMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured log event found for: " + message));
        return new String(encoder.encode(event), StandardCharsets.UTF_8).strip();
    }

    @Override
    public void close() {
        logger.detachAppender(capture);
        capture.stop();
    }
}
