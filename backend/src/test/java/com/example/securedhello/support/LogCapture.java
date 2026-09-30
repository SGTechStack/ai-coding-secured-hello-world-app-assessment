package com.example.securedhello.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test seam for log output (spec Seam 4). Marks the end of the real application-log, audit-log and
 * email files, then returns every line written after the mark, each parsed as JSON. Reading the files
 * the app is configured with (rather than a stand-in appender) proves both the format and the
 * destination. Parsing fails the test on any line that is not a single JSON object or that has
 * duplicate keys.
 */
public final class LogCapture {

	public static final String APPLICATION_APPENDER = "APPLICATION_FILE";

	public static final String AUDIT_APPENDER = "AUDIT_FILE";

	public static final String AUDIT_LOGGER = "audit";

	public static final String EMAIL_APPENDER = "EMAIL_FILE";

	public static final String EMAIL_LOGGER = "email";

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	private final Path applicationFile;

	private final Path auditFile;

	private final long applicationMark;

	private final long auditMark;

	private final Path emailFile;

	private final long emailMark;

	private LogCapture(Path applicationFile, Path auditFile, Path emailFile) {
		this.applicationFile = applicationFile;
		this.auditFile = auditFile;
		this.emailFile = emailFile;
		this.applicationMark = size(applicationFile);
		this.auditMark = size(auditFile);
		this.emailMark = size(emailFile);
	}

	/** Starts capturing at the current end of the application, audit and email log files. */
	public static LogCapture start() {
		return new LogCapture(applicationLogFile(), auditLogFile(), emailLogFile());
	}

	/** The {@code EmailService} stub's file, standing in for a mailbox. */
	public static Path emailLogFile() {
		return file(context().getLogger(EMAIL_LOGGER), EMAIL_APPENDER);
	}

	/** Email-file lines written since the mark, each parsed as a JSON object. */
	public List<JsonNode> email() {
		return parse(linesSince(emailFile, emailMark));
	}

	/** Raw email-file lines written since the mark. */
	public List<String> emailText() {
		return linesSince(emailFile, emailMark);
	}

	public static Path applicationLogFile() {
		return file(root(), APPLICATION_APPENDER);
	}

	public static Path auditLogFile() {
		return file(context().getLogger(AUDIT_LOGGER), AUDIT_APPENDER);
	}

	/** Raw application-log lines written since the mark. */
	public List<String> applicationText() {
		return linesSince(applicationFile, applicationMark);
	}

	/** Raw audit-log lines written since the mark. */
	public List<String> auditText() {
		return linesSince(auditFile, auditMark);
	}

	/** Application-log lines written since the mark, each parsed as a JSON object. */
	public List<JsonNode> application() {
		return parse(applicationText());
	}

	/** Audit-log lines written since the mark, each parsed as a JSON object. */
	public List<JsonNode> audit() {
		return parse(auditText());
	}

	public List<JsonNode> application(Predicate<JsonNode> filter) {
		return application().stream().filter(filter).toList();
	}

	public List<JsonNode> audit(Predicate<JsonNode> filter) {
		return audit().stream().filter(filter).toList();
	}

	/** Reads a dotted ECS field (for example {@code trace.id}) from nested JSON. */
	public static String field(JsonNode line, String dottedPath) {
		JsonNode node = node(line, dottedPath);
		return (node == null || node.isNull()) ? null : (node.isValueNode() ? node.asString() : node.toString());
	}

	public static JsonNode node(JsonNode line, String dottedPath) {
		JsonNode node = line;
		for (String part : dottedPath.split("\\.")) {
			if (node == null) {
				return null;
			}
			node = node.get(part);
		}
		return node;
	}

	public static Predicate<JsonNode> hasField(String dottedPath, String value) {
		return (line) -> value.equals(field(line, dottedPath));
	}

	/** Waits until a line matching the filter reaches the audit log, or fails. */
	public List<JsonNode> awaitAudit(Predicate<JsonNode> filter) {
		for (int attempt = 0; attempt < 50; attempt++) {
			List<JsonNode> matches = audit(filter);
			if (!matches.isEmpty()) {
				return matches;
			}
			pause();
		}
		return fail("Expected audit event not written");
	}

	private static void pause() {
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static List<JsonNode> parse(List<String> lines) {
		List<JsonNode> parsed = new ArrayList<>();
		for (String line : lines) {
			try {
				JsonNode node = JSON.readTree(line);
				assertThat(node.isObject()).as("log line is a JSON object: %s", line).isTrue();
				parsed.add(node);
			}
			catch (RuntimeException ex) {
				fail("Log line is not valid JSON: " + line, ex);
			}
		}
		return parsed;
	}

	private static List<String> linesSince(Path file, long mark) {
		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			long length = raf.length();
			long start = (length < mark) ? 0 : mark;
			byte[] bytes = new byte[(int) (length - start)];
			raf.seek(start);
			raf.readFully(bytes);
			String text = new String(bytes, StandardCharsets.UTF_8);
			return text.isEmpty() ? List.of() : List.of(text.split("\n"));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot read log file " + file, ex);
		}
	}

	private static long size(Path file) {
		try {
			return Files.exists(file) ? Files.size(file) : 0;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Path file(Logger logger, String appenderName) {
		if (!(logger.getAppender(appenderName) instanceof FileAppender<?> appender)) {
			return fail("No file appender '%s' on logger '%s'", appenderName, logger.getName());
		}
		return Path.of(appender.getFile()).toAbsolutePath();
	}

	private static Logger root() {
		return context().getLogger(Logger.ROOT_LOGGER_NAME);
	}

	private static LoggerContext context() {
		return (LoggerContext) LoggerFactory.getILoggerFactory();
	}

}
