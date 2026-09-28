package com.example.helloauth.web;

import com.example.helloauth.service.exception.AuthExceptions.AccountNotFoundException;
import com.example.helloauth.service.exception.AuthExceptions.InvalidCredentialsException;
import com.example.helloauth.service.exception.AuthExceptions.InvalidResetTokenException;
import com.example.helloauth.service.exception.AuthExceptions.RegistrationConflictException;
import com.example.helloauth.service.exception.AuthExceptions.SelfActionForbiddenException;
import com.example.helloauth.service.exception.AuthExceptions.TooManyRequestsException;
import com.example.helloauth.service.exception.AuthExceptions.WeakPasswordException;
import com.example.helloauth.web.dto.ApiPayloads.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The complete map from service failures to HTTP responses.
 *
 * <p>Keeping it in one file is the point: the status code for every failure the API can produce is
 * readable side by side, which is how you notice that two responses which must be indistinguishable
 * still are. Handlers scattered across controllers make that impossible to check.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 401 with a message that says nothing about which part was wrong. Unknown username, wrong
     * password, locked account and disabled account all arrive here and all leave looking identical
     * — that is the PRD's enumeration-resistance requirement, and it is only actually true because
     * there is a single handler rather than one per case.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> onInvalidCredentials(InvalidCredentialsException e) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "invalid_credentials",
                "Invalid username or password.");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> onTooManyRequests(TooManyRequestsException e) {
        return respond(
                HttpStatus.TOO_MANY_REQUESTS,
                "too_many_requests",
                "Too many attempts from your address. Try again later.");
    }

    /** 409, with the offending field named, because the PRD asks registration to be specific. */
    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiError> onRegistrationConflict(RegistrationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        new ApiError(
                                "registration_conflict",
                                e.getMessage(),
                                Map.of(e.getField(), e.getMessage())));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiError> onWeakPassword(WeakPasswordException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("weak_password", e.getMessage(), Map.of("password", e.getMessage())));
    }

    /** Unknown, expired and already-used tokens are one response, for the same reason as above. */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiError> onInvalidResetToken(InvalidResetTokenException e) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "invalid_reset_token",
                "That reset link is invalid or has expired. Request a new one.");
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> onAccountNotFound(AccountNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "account_not_found", "No such account.");
    }

    /**
     * 409 rather than 403. The caller is a legitimate admin, so a 403 here would be
     * indistinguishable from "you are not an admin" — which is what {@code /api/admin/**} returns to
     * everyone else — and the frontend could not tell the two situations apart.
     */
    @ExceptionHandler(SelfActionForbiddenException.class)
    public ResponseEntity<ApiError> onSelfAction(SelfActionForbiddenException e) {
        return respond(HttpStatus.CONFLICT, "self_action_forbidden", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new ApiError(
                                "validation_failed",
                                "Some of the details you entered are not valid.",
                                fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException e) {
        return respond(
                HttpStatus.BAD_REQUEST, "malformed_request", "The request body could not be read.");
    }

    private static ResponseEntity<ApiError> respond(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }
}
