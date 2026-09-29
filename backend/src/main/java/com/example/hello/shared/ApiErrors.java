package com.example.hello.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> application(ApiException error) {
    return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException error) {
    Map<String, String> fields = new LinkedHashMap<>();
    error
        .getBindingResult()
        .getFieldErrors()
        .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Please check the highlighted fields.", "errors", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed() {
    return ResponseEntity.badRequest().body(Map.of("message", "Invalid request."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("message", "Username or email is already registered."));
  }
}
