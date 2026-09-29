package com.example.securedhello.controller.error;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.securedhello.controller.AdminController;
import com.example.securedhello.service.AdminTargetNotFoundException;
import com.example.securedhello.service.SelfActionException;

/**
 * Translates admin-mutation failures to HTTP responses: the Self-Action Guard
 * yields 409, and an unknown target yields 404. Messages are short and
 * non-sensitive.
 */
@RestControllerAdvice(assignableTypes = AdminController.class)
public class AdminExceptionHandler {

    @ExceptionHandler(SelfActionException.class)
    public ResponseEntity<Map<String, String>> handleSelfAction(SelfActionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AdminTargetNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AdminTargetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
