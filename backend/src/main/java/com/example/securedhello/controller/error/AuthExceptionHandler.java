package com.example.securedhello.controller.error;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.securedhello.controller.AuthController;
import com.example.securedhello.service.AuthenticationFailedException;

/**
 * Translates login failures into a single generic 401 response, regardless of
 * whether the Username exists, the password was wrong, or the account was
 * disabled — preserving Enumeration Resistance. Malformed requests (missing
 * fields) also collapse to the same generic 401 so the shape of the failure
 * never leaks account state.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    private static final Map<String, String> GENERIC_ERROR =
            Map.of("error", "Invalid username or password");

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthFailure(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GENERIC_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GENERIC_ERROR);
    }
}
