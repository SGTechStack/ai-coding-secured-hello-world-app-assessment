package com.sgtechstack.helloauth.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import org.springframework.stereotype.Component;

/**
 * Security audit trail written as structured log lines on the {@code AUDIT} logger.
 * <p>
 * Each field is emitted twice: as an SLF4J key-value pair (picked up by structured JSON
 * formats such as ECS in the prod profile) and as {@code key=value} text in the message so
 * plain-text dev logs stay greppable. Values are sanitized against log injection. Callers must
 * never pass secrets; there is deliberately no field for passwords or tokens.
 */
@Component
public class AuditLog {

	private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

	private static final int MAX_VALUE_LENGTH = 128;

	public Entry event(AuditEvent event) {
		return new Entry(event);
	}

	static String sanitize(Object value) {
		if (value == null) {
			return "-";
		}
		String text = value.toString();
		if (text.length() > MAX_VALUE_LENGTH) {
			text = text.substring(0, MAX_VALUE_LENGTH) + "...";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isISOControl(c) || c == ' ' || c == ' ') {
				out.append(String.format("\\u%04x", (int) c));
			}
			else if (c == '"' || c == '\\') {
				out.append('\\').append(c);
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static String formatValue(String sanitized) {
		boolean needsQuotes = sanitized.isEmpty() || sanitized.chars().anyMatch(c -> c == ' ' || c == '=');
		return needsQuotes ? '"' + sanitized + '"' : sanitized;
	}

	public static final class Entry {

		private final Map<String, String> fields = new LinkedHashMap<>();

		private Entry(AuditEvent event) {
			this.fields.put("event", event.name());
		}

		/** Who performed the action (username), when authenticated. */
		public Entry actor(String actor) {
			return with("actor", actor);
		}

		/** The account acted upon (username). */
		public Entry target(String target) {
			return with("target", target);
		}

		public Entry ip(String ip) {
			return with("ip", ip);
		}

		public Entry reason(String reason) {
			return with("reason", reason);
		}

		public Entry with(String key, Object value) {
			this.fields.put(key, sanitize(value));
			return this;
		}

		public void log() {
			LoggingEventBuilder builder = LOG.atInfo();
			this.fields.forEach(builder::addKeyValue);
			builder.log(this.fields.entrySet()
				.stream()
				.map(field -> field.getKey() + "=" + formatValue(field.getValue()))
				.collect(Collectors.joining(" ")));
		}

	}

}
