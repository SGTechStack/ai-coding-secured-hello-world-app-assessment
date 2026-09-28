package com.example.helloworldauth.web;

import com.example.helloworldauth.admin.AdminUserNotFoundException;
import com.example.helloworldauth.admin.SelfActionForbiddenException;
import com.example.helloworldauth.auth.AuthenticationFailedException;
import com.example.helloworldauth.auth.InvalidResetTokenException;
import com.example.helloworldauth.auth.RegistrationConflictException;
import com.example.helloworldauth.auth.TooManyRequestsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> onAuthFailed(AuthenticationFailedException ex) {
        // Single generic 401 — never reveal whether the username exists.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "unauthorized", "message", ex.getMessage()));
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<Map<String, String>> onConflict(RegistrationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "conflict", "message", ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> onThrottled(TooManyRequestsException ex) {
        // IP-level throttle tripped (ticket 07). Generic body — no per-account detail.
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("error", "too_many_requests", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<Map<String, String>> onInvalidResetToken(InvalidResetTokenException ex) {
        // Generic 400 for unknown/expired/used token — never reveal which case.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "invalid_reset_token", "message", ex.getMessage()));
    }

    @ExceptionHandler(SelfActionForbiddenException.class)
    public ResponseEntity<Map<String, String>> onSelfAction(SelfActionForbiddenException ex) {
        // Story 9 self-action guard — an admin cannot change their own account status.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "self_action_forbidden", "message", ex.getMessage()));
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<Map<String, String>> onAdminUserNotFound(AdminUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> onValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "validation", "message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> onUnreadable(HttpMessageNotReadableException ex) {
        // Malformed/unparseable body — e.g. an invalid enum value for a role
        // change (Story 10) that is neither USER nor ADMIN. Generic 400, no
        // internal detail leaked from the parser message.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "bad_request", "message", "malformed request body"));
    }
}
