package com.sgtechstack.helloauth.web;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * An expected, client-facing failure. Rendered by {@link ApiExceptionHandler} as an RFC 9457
 * problem detail whose {@code detail} is the exception message, so messages must be safe to
 * show to anyone.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	protected ApiException(HttpStatus status, String code, String detail) {
		// No stack trace: these are control flow for expected outcomes, not bugs.
		super(detail, null, false, false);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String code() {
		return this.code;
	}

	/** Field-level messages, keyed by request field name. */
	public Map<String, String> errors() {
		return Map.of();
	}

	public HttpHeaders headers() {
		return HttpHeaders.EMPTY;
	}

}
