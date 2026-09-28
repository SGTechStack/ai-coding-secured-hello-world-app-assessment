package com.example.helloworldauth.web;

import com.example.helloworldauth.auth.AuthenticationFailedException;
import com.example.helloworldauth.auth.RegistrationConflictException;
import com.example.helloworldauth.auth.TooManyRequestsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> onValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "validation", "message", message));
    }
}
