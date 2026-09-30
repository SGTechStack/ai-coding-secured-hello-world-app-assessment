package com.example.securedhello.observability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.ratelimit.ClientAddressLimit;
import com.example.securedhello.ratelimit.RateLimitedByClientAddress;

/**
 * {@code POST /client-events}: the SPA tells the operator that something failed in the browser, or how
 * long a page took to load (story 111). Public, because a page can fail before anyone has logged in,
 * and rate-limited per direct client address so it cannot be used to flood the application log. CSRF
 * does not apply: this is the one exempt endpoint (ADR 0002), because bootstrapping a token would make
 * the server persist a Session for every Visitor that merely loaded a page, and the SPA sends the
 * report without credentials so no Session is resolved at all.
 * <p>
 * The body is deliberately narrow: a fixed {@link ClientEventKind}, a path in the SPA's own shape, and
 * a duration. There is no field for a message, a stack trace, an identifier or anything typed into the
 * SPA, so no report can carry user data or error details. A value that breaks one of those rules is
 * 400 {@code validation}; a field the record does not declare is dropped before it reaches this method
 * and is never logged or recorded, because the application does not fail on unknown JSON properties
 * (the same setting that makes registration ignore a {@code role} in its body).
 */
@RestController
@RequestMapping("${app.api.base-path}")
class ClientEventController {

	/**
	 * One report.
	 *
	 * @param kind what happened; unknown values are rejected
	 * @param path the SPA path it happened on, in the SPA's own shape (a query string or fragment
	 * would be where a Reset Token travels, so neither is accepted)
	 * @param durationMs how long it took, required for a timed kind and refused for an error kind
	 */
	record ClientEventRequest(@NotNull ClientEventKind kind,
			@NotNull @Pattern(regexp = "/[A-Za-z0-9/_-]{0,63}") String path,
			@PositiveOrZero @Max(MAX_DURATION_MS) Long durationMs) {

		/** A timed kind is the one that carries a duration, so a report cannot mean two things at once. */
		@AssertTrue
		boolean isDurationMatchingKind() {
			return this.kind == null || this.kind.isTimed() == (this.durationMs != null);
		}

	}

	/** A page load longer than an hour is not a measurement worth recording. */
	static final long MAX_DURATION_MS = 3_600_000L;

	private final ClientEventRecorder recorder;

	ClientEventController(ClientEventRecorder recorder) {
		this.recorder = recorder;
	}

	/** Rate-limited before the body is validated, so a malformed report spends quota too (issue 18). */
	@PostMapping("/client-events")
	@RateLimitedByClientAddress(ClientAddressLimit.CLIENT_EVENTS)
	ResponseEntity<Void> report(@Valid @RequestBody ClientEventRequest body) {
		this.recorder.record(body.kind(), body.path(), body.durationMs());
		return ResponseEntity.noContent().build();
	}

}
