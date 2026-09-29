package com.sgtechstack.helloauth.web;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

	private final Duration retryAfter;

	public TooManyRequestsException(Duration retryAfter) {
		super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Too many attempts. Try again later.");
		this.retryAfter = retryAfter;
	}

	@Override
	public HttpHeaders headers() {
		HttpHeaders headers = new HttpHeaders();
		// Round up so clients never retry before the window has actually reopened.
		long seconds = Math.max(1, this.retryAfter.plusMillis(999).toSeconds());
		headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		return headers;
	}

}
