package com.example.securedhello.observability;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Where a report from the SPA lands, so that an operator can see it: a Micrometer meter, scraped from
 * the {@code prometheus} endpoint on the management port, and one line in the application log.
 * <p>
 * An error kind increments {@code app.client.errors} and is logged at WARN; a timed kind records
 * {@code app.client.performance} and is logged at DEBUG, because a load timing is a number to watch,
 * not something to read. Both meters are tagged only with the {@link ClientEventKind}, so a client
 * cannot grow the metric's cardinality; the page path goes in the log line, where the encoder escapes
 * it.
 */
@Component
class ClientEventRecorder {

	static final String ERRORS_METRIC = "app.client.errors";

	static final String PERFORMANCE_METRIC = "app.client.performance";

	private static final Logger log = LoggerFactory.getLogger(ClientEventRecorder.class);

	private final MeterRegistry meters;

	ClientEventRecorder(MeterRegistry meters) {
		this.meters = meters;
	}

	/**
	 * Records one report.
	 * @param kind what the SPA reported
	 * @param path the SPA path it happened on
	 * @param durationMs how long it took, for a {@link ClientEventKind#isTimed() timed} kind, and
	 * {@code null} for an error kind
	 */
	void record(ClientEventKind kind, String path, Long durationMs) {
		if (kind.isTimed()) {
			Timer.builder(PERFORMANCE_METRIC)
				.description("Page timings the SPA reported")
				.tag("kind", kind.name())
				.publishPercentileHistogram()
				.register(this.meters)
				.record(Duration.ofMillis(durationMs));
			log.atDebug()
				.addKeyValue("event.action", "client-performance")
				.addKeyValue("client.event.kind", kind.name())
				.addKeyValue("client.url.path", path)
				.addKeyValue("event.duration", TimeUnit.MILLISECONDS.toNanos(durationMs))
				.log("The SPA reported a page timing");
			return;
		}
		Counter.builder(ERRORS_METRIC)
			.description("Client-side errors the SPA reported")
			.tag("kind", kind.name())
			.register(this.meters)
			.increment();
		log.atWarn()
			.addKeyValue("event.action", "client-error")
			.addKeyValue("client.event.kind", kind.name())
			.addKeyValue("client.url.path", path)
			.log("The SPA reported a client-side error");
	}

}
