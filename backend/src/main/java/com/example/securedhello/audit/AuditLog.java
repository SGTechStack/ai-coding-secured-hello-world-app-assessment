package com.example.securedhello.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * The one place that emits security audit events. Events go through the SLF4J fluent API, with
 * metadata only as key-value pairs and a static message, to the dedicated {@code audit} logger. That
 * logger writes only to the rolling audit file (see {@code logback-spring.xml}), never to the
 * application log.
 * <p>
 * Every event carries {@code trace.id}: inside a request it is the request's trace; outside one (for
 * example startup and shutdown) the event gets a trace of its own.
 */
@Component
public class AuditLog {

	public static final String LOGGER_NAME = "audit";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	private final Tracer tracer;

	public AuditLog(Tracer tracer) {
		this.tracer = tracer;
	}

	public void record(AuditEvent event) {
		if (tracer.currentSpan() != null) {
			emit(event);
			return;
		}
		Span span = tracer.nextSpan().name("audit-event").start();
		try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
			emit(event);
		}
		finally {
			span.end();
		}
	}

	private static void emit(AuditEvent event) {
		LoggingEventBuilder builder = log.atLevel(event.level());
		event.fields().forEach(builder::addKeyValue);
		builder.log("Audit event");
	}

}
