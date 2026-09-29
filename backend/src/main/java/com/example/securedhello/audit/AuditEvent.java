package com.example.securedhello.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.event.Level;

import com.example.securedhello.logging.CorrelationFilter;
import com.example.securedhello.logging.LogSanitizer;

/**
 * One security event for the {@link AuditLog}. Start from {@link #success}, {@link #failure} or
 * {@link #systemFailure}, which fix {@code event.outcome} and the level (INFO, WARN, ERROR), then add
 * the contract fields that apply. Accounts are identified only by UUID; {@code trace.id} is added by
 * the audit log itself.
 */
public final class AuditEvent {

	private final Level level;

	private final Map<String, Object> fields = new LinkedHashMap<>();

	private AuditEvent(Level level, AuditAction action, String outcome, String reason) {
		this.level = level;
		fields.put("event.action", action.value());
		fields.put("event.outcome", outcome);
		if (reason != null) {
			fields.put("event.reason", reason);
		}
	}

	/** A successful security event, logged at INFO. */
	public static AuditEvent success(AuditAction action) {
		return new AuditEvent(Level.INFO, action, "success", null);
	}

	/**
	 * A rejected or failed attempt (bad credentials, lockout, rate limit, 403, CSRF, validation,
	 * rejected admin action), logged at WARN.
	 * @param reason generic, machine-readable reason such as {@code csrf_invalid}
	 */
	public static AuditEvent failure(AuditAction action, String reason) {
		return new AuditEvent(Level.WARN, action, "failure", reason);
	}

	/** An authentication system failure, such as the database being unavailable, logged at ERROR. */
	public static AuditEvent systemFailure(AuditAction action, String reason) {
		return new AuditEvent(Level.ERROR, action, "failure", reason);
	}

	/** The acting Account's UUID. */
	public AuditEvent userId(UUID userId) {
		fields.put("user.id", userId.toString());
		return this;
	}

	/** The target Account's UUID for admin actions. */
	public AuditEvent targetUserId(UUID targetUserId) {
		fields.put("target.user.id", targetUserId.toString());
		return this;
	}

	/** State before and after a change, written as {@code state.before.<field>} and {@code state.after.<field>}. */
	public AuditEvent change(String field, Object before, Object after) {
		fields.put("state.before." + field, before);
		fields.put("state.after." + field, after);
		return this;
	}

	/** {@code authentication.method: password} on authentication events. */
	public AuditEvent passwordAuthentication() {
		fields.put("authentication.method", "password");
		return this;
	}

	/**
	 * Correlates an event before authentication without logging the Session ID: writes
	 * {@code session.hash}, the SHA-256 of the given Session ID.
	 */
	public AuditEvent sessionHash(String sessionId) {
		fields.put("session.hash", sha256(sessionId));
		return this;
	}

	/** {@code source.ip_hash}: an already keyed hash of the client address, never the address itself. */
	public AuditEvent sourceIpHash(String sourceIpHash) {
		fields.put("source.ip_hash", sourceIpHash);
		return this;
	}

	/** {@code event.type}, for example {@code change} on a Password Change. */
	public AuditEvent eventType(String... types) {
		fields.put("event.type", List.of(types));
		return this;
	}

	/** {@code url.path} (sanitised, no query string) and {@code http.request.method} of the request. */
	public AuditEvent request(HttpServletRequest request) {
		fields.put("url.path", CorrelationFilter.urlPath(request));
		fields.put("http.request.method", LogSanitizer.escapeControl(request.getMethod()));
		return this;
	}

	Level level() {
		return level;
	}

	Map<String, Object> fields() {
		return fields;
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
