package com.example.auth.web;

import com.example.auth.exception.ConflictException;
import com.example.auth.passwordreset.InvalidResetTokenException;
import com.example.auth.passwordreset.ResetRequestThrottledException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates domain exceptions to RFC 9457 ProblemDetail responses.
 * Validation errors (MethodArgumentNotValidException) are handled automatically
 * via spring.mvc.problemdetails.enabled=true in application.yml.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /** Invalid / expired / already-used reset token → generic 400. */
    @ExceptionHandler(InvalidResetTokenException.class)
    ProblemDetail handleInvalidResetToken(InvalidResetTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail("Invalid or expired reset token");
        return problem;
    }

    /** Reset-request rate limit exceeded for the source IP → 429. */
    @ExceptionHandler(ResetRequestThrottledException.class)
    ProblemDetail handleResetThrottled(ResetRequestThrottledException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setDetail("Too many requests");
        return problem;
    }
}
