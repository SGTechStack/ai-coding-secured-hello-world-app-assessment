package com.example.securedhello.logging;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Neutralises text before it reaches a log line: escapes control characters (log injection) and
 * redacts credentials and emails from exception text.
 */
public final class LogSanitizer {

	public static final String MASK = "***MASKED***";

	private static final String[] SENSITIVE_KEY_PARTS = { "password", "token", "secret", "csrf", "sessionid", "email",
			"username" };

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final Pattern KEY_VALUE = Pattern.compile(
			"(?i)\\b(password|passwd|token|secret|csrf|session[_-]?id|username|email)\\b(\\s*[=:]\\s*)('[^']*'|\"[^\"]*\"|\\S+)");

	private LogSanitizer() {
	}

	/** Escapes CR, LF, tab and every other control or line-separator character. */
	public static String escapeControl(String value) {
		StringBuilder escaped = null;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			String replacement = replacement(c);
			if (replacement != null && escaped == null) {
				escaped = new StringBuilder(value.length() + 16).append(value, 0, i);
			}
			if (escaped != null) {
				escaped.append((replacement != null) ? replacement : String.valueOf(c));
			}
		}
		return (escaped != null) ? escaped.toString() : value;
	}

	private static String replacement(char c) {
		return switch (c) {
			case '\r' -> "\\r";
			case '\n' -> "\\n";
			case '\t' -> "\\t";
			default -> (Character.isISOControl(c) || c == ' ' || c == ' ')
					? String.format("\\u%04x", (int) c) : null;
		};
	}

	/** Redacts emails and {@code key=value} credentials from exception text, keeping line breaks. */
	public static String redact(String text) {
		String redacted = KEY_VALUE.matcher(text).replaceAll("$1$2" + MASK);
		return EMAIL.matcher(redacted).replaceAll(MASK);
	}

	/** An exception message made safe for a single log field. */
	public static String exceptionMessage(String message) {
		return escapeControl(redact(message));
	}

	/**
	 * Whether a log key names data that must never be logged in clear: password, token, secret,
	 * csrf, session id, email or username, ignoring case and separators.
	 */
	public static boolean isSensitiveKey(String key) {
		String normalised = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		for (String part : SENSITIVE_KEY_PARTS) {
			if (normalised.contains(part)) {
				return true;
			}
		}
		return false;
	}

}
