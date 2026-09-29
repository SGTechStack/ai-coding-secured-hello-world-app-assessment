package com.example.securedhello.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception reaching Spring MVC into an RFC 9457 ProblemDetail with a {@code code}.
 * Unexpected exceptions become a generic 500 that never exposes messages, class names, SQL or
 * stack traces.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";

	static final String INTERNAL_ERROR_CODE = "internal_error";

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) throws Exception {
		if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
			// Not unexpected: Spring Security's filters turn these into 403 / 401.
			throw exception;
		}
		ErrorCategory category = ErrorCategory.of(exception);
		// Logged once, here; the encoder maps error_* to error.* and sanitises the exception text.
		log.atError()
			.setCause(exception)
			.addKeyValue("error_code", INTERNAL_ERROR_CODE)
			.addKeyValue("error_category", category.value())
			.addKeyValue("error_follow_up_action", category.followUpAction())
			.log("Unexpected exception while handling request");
		return ProblemResponses.problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE, INTERNAL_ERROR_DETAIL);
	}

	/**
	 * Adds the {@code code} field to the ProblemDetail Spring builds for standard MVC exceptions. Spring
	 * often passes a null body here and creates the ProblemDetail inside {@code super}, so the code is
	 * added to the result.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problem.setProperty(ProblemResponses.CODE, codeFor(response.getStatusCode()));
		}
		return response;
	}

	/** The {@code code} for a status that Spring MVC or the container produced. */
	static String codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "validation";
			case 404 -> "not_found";
			case 405 -> "method_not_allowed";
			case 415 -> "unsupported_media_type";
			default -> status.is5xxServerError() ? "internal_error" : "request_error";
		};
	}

}
