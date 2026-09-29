package com.example.securedhello.controller.error;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.securedhello.controller.RegistrationController;
import com.example.securedhello.service.DuplicateAccountException;

/**
 * Translates registration failures into HTTP responses without leaking
 * internals. Validation failures (weak password, missing/invalid fields)
 * become 400; Username/Email conflicts become 409. Response bodies carry a
 * short, generic message only.
 */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Registration request is invalid"));
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateAccountException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
