package com.example.securedhello.logging;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.ElasticCommonSchemaProperties;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;

/**
 * The custom encoder behind every log line: newline-delimited ECS JSON with RFC 3339 timestamps in
 * UTC+8. On top of Spring Boot's ECS layout it
 * <ul>
 * <li>renames Micrometer Tracing's {@code traceId} / {@code spanId} MDC keys to {@code trace.id} /
 * {@code span.id};</li>
 * <li>maps the {@code error_code}, {@code error_category} and {@code error_follow_up_action} keys to
 * {@code error.code}, {@code error.category} and {@code error.follow_up_action}, in the same
 * {@code error} object as the attached exception's type, message and stack trace;</li>
 * <li>masks the value of any sensitive key as {@code ***MASKED***};</li>
 * <li>escapes control characters in every message and context value, and redacts exception text.</li>
 * </ul>
 * Dotted keys are written as nested objects, as ECS expects. Configured in {@code logback-spring.xml}.
 */
public class EcsLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

	/** RFC 3339 with milliseconds, in UTC+8. */
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
		.withZone(ZoneId.of("Asia/Singapore"));

	private static final Map<String, String> RENAMED_KEYS = Map.of("traceId", "trace.id", "spanId", "span.id",
			"error_code", "error.code", "error_category", "error.category", "error_follow_up_action",
			"error.follow_up_action");

	private final JsonWriter<Map<String, Object>> writer = JsonWriter.<Map<String, Object>>standard()
		.withNewLineAtEnd();

	private final Map<String, Object> service;

	private final Long pid;

	public EcsLogFormatter(Environment environment) {
		ElasticCommonSchemaProperties.Service properties = ElasticCommonSchemaProperties.get(environment).service();
		this.service = new LinkedHashMap<>();
		putIfPresent(this.service, "name", properties.name());
		putIfPresent(this.service, "version", properties.version());
		putIfPresent(this.service, "environment", properties.environment());
		this.pid = environment.getProperty("spring.application.pid", Long.class);
	}

	@Override
	public String format(ILoggingEvent event) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("@timestamp", TIMESTAMP.format(event.getInstant()));
		Map<String, Object> logFields = new LinkedHashMap<>();
		logFields.put("level", event.getLevel().toString());
		logFields.put("logger", event.getLoggerName());
		json.put("log", logFields);
		Map<String, Object> process = new LinkedHashMap<>();
		putIfPresent(process, "pid", this.pid);
		process.put("thread", Map.of("name", event.getThreadName()));
		json.put("process", process);
		json.put("service", new LinkedHashMap<>(this.service));
		json.put("message", LogSanitizer.escapeControl(String.valueOf(event.getFormattedMessage())));
		event.getMDCPropertyMap().forEach((key, value) -> putContext(json, key, value));
		List<KeyValuePair> pairs = event.getKeyValuePairs();
		if (pairs != null) {
			pairs.forEach((pair) -> putContext(json, pair.key, pair.value));
		}
		IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable != null) {
			putNested(json, "error.type", throwable.getClassName());
			putNested(json, "error.message", LogSanitizer.exceptionMessage(String.valueOf(throwable.getMessage())));
			putNested(json, "error.stack_trace", LogSanitizer.redact(ThrowableProxyUtil.asString(throwable)));
		}
		json.put("ecs", Map.of("version", "8.11"));
		return this.writer.writeToString(json);
	}

	private static void putContext(Map<String, Object> json, String key, Object value) {
		String name = RENAMED_KEYS.getOrDefault(key, key);
		putNested(json, name, LogSanitizer.isSensitiveKey(name) ? LogSanitizer.MASK : sanitise(value));
	}

	private static Object sanitise(Object value) {
		if (value instanceof CharSequence text) {
			return LogSanitizer.escapeControl(text.toString());
		}
		if (value instanceof Collection<?> values) {
			List<Object> sanitised = new ArrayList<>(values.size());
			values.forEach((item) -> sanitised.add(sanitise(item)));
			return sanitised;
		}
		if (value == null || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		return LogSanitizer.escapeControl(value.toString());
	}

	/** Puts a dotted key as nested objects; falls back to the flat key if the path is taken by a value. */
	@SuppressWarnings("unchecked")
	private static void putNested(Map<String, Object> json, String dottedKey, Object value) {
		String[] parts = dottedKey.split("\\.");
		Map<String, Object> target = json;
		for (int i = 0; i < parts.length - 1; i++) {
			Object existing = target.get(parts[i]);
			if (existing == null) {
				Map<String, Object> child = new LinkedHashMap<>();
				target.put(parts[i], child);
				target = child;
			}
			else if (existing instanceof LinkedHashMap<?, ?> child) {
				target = (Map<String, Object>) child;
			}
			else {
				json.put(dottedKey, value);
				return;
			}
		}
		String leaf = parts[parts.length - 1];
		if (target.get(leaf) instanceof Map<?, ?>) {
			json.put(dottedKey, value);
			return;
		}
		target.put(leaf, value);
	}

	private static void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !(value instanceof String text && text.isEmpty())) {
			map.put(key, value);
		}
	}

}
