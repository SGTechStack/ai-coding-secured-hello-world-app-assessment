package com.example.securedhello.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.audit.SourceIpHash;
import com.example.securedhello.credential.PasswordPolicyException;
import com.example.securedhello.ratelimit.RateLimitExceededException;

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

	private final AuditLog auditLog;

	private final SourceIpHash sourceIpHash;

	GlobalExceptionHandler(AuditLog auditLog, SourceIpHash sourceIpHash) {
		this.auditLog = auditLog;
		this.sourceIpHash = sourceIpHash;
	}

	/** 400 {@code password_policy} listing every broken Credential policy rule. */
	@ExceptionHandler(PasswordPolicyException.class)
	ProblemDetail handlePasswordPolicy(PasswordPolicyException exception, HttpServletRequest request) {
		auditInputFailure(request, "password_policy", List.of("password"));
		ProblemDetail problem = ProblemResponses.problem(HttpStatus.BAD_REQUEST, "password_policy",
				"The password does not meet the password policy.");
		problem.setProperty("violations", exception.violations());
		return problem;
	}

	/**
	 * 429 {@code too_many_requests} with {@code Retry-After} for every rate limiter and the IP
	 * Throttle, audited with the endpoint, and with {@code source.ip_hash} (never the address) when
	 * the limit is per client address. The body uses the Standard's wording and never says which limit
	 * or key was hit.
	 */
	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimited(RateLimitExceededException exception, HttpServletRequest request) {
		AuditEvent event = AuditEvent.failure(AuditAction.ACCESS_CONTROL, exception.reason())
			.request(request)
			.sessionHashOf(request);
		if (exception.keyedByClientAddress()) {
			event.sourceIpHash(sourceIpHash.of(request));
		}
		auditLog.record(event);
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
			.body(ProblemResponses.problem(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", "too many requests"));
	}

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

	/** 400 {@code validation} naming the failing fields (never their values), audited once. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<String> fields = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getField)
			.distinct()
			.sorted()
			.toList();
		auditInputFailure(servletRequest(request), "validation", fields);
		ProblemDetail problem = ProblemResponses.problem(HttpStatus.BAD_REQUEST, "validation",
				"The request is invalid.");
		problem.setProperty("fields", fields);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** A body that is not the expected JSON is an input-validation failure too. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		auditInputFailure(servletRequest(request), "validation", List.of());
		return super.handleHttpMessageNotReadable(ex, headers, status, request);
	}

	private void auditInputFailure(HttpServletRequest request, String reason, List<String> fields) {
		auditLog.record(AuditEvent.failure(AuditAction.ACCESS_CONTROL, reason)
			.invalidFields(fields)
			.request(request)
			.sessionHashOf(request));
	}

	private static HttpServletRequest servletRequest(WebRequest request) {
		return ((ServletWebRequest) request).getRequest();
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
