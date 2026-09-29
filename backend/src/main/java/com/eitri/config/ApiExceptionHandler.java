package com.eitri.config;

import com.eitri.logging.SanitizedLogException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions that escape a controller into {@link ApiError} bodies that never carry exception
 * messages or stack traces. Features that need a specific message (e.g. a failed login) return it themselves.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Spring MVC's own exceptions (bad JSON, unknown path, wrong method, ...) keep their status. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return super.handleExceptionInternal(ex, ApiError.of(status), headers, status, request);
    }

    @ExceptionHandler
    ResponseEntity<ApiError> handleUnexpected(Exception ex) throws Exception {
        // Left to Spring Security, which answers 401/403 (e.g. for @PreAuthorize denials).
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        LOGGER.atError()
                .setCause(SanitizedLogException.from(ex, "Unexpected application failure"))
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .setMessage("Unhandled exception.")
                .log();
        return ResponseEntity.internalServerError().body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
