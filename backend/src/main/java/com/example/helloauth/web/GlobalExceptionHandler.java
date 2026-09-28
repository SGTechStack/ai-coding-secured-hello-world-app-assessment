package com.example.helloauth.web;

import com.example.helloauth.service.ServiceExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceExceptions.AuthenticationFailedException.class)
    public ResponseEntity<Dtos.MessageResponse> handleAuthFailed(ServiceExceptions.AuthenticationFailedException e) {
        // Generic, identical for unknown user / bad password / locked (enumeration resistance).
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Dtos.MessageResponse("Invalid username or password."));
    }

    @ExceptionHandler(ServiceExceptions.ThrottledException.class)
    public ResponseEntity<Dtos.MessageResponse> handleThrottled(ServiceExceptions.ThrottledException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new Dtos.MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(ServiceExceptions.ValidationException.class)
    public ResponseEntity<Dtos.MessageResponse> handleValidation(ServiceExceptions.ValidationException e) {
        return ResponseEntity.badRequest().body(new Dtos.MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(ServiceExceptions.SelfActionException.class)
    public ResponseEntity<Dtos.MessageResponse> handleSelfAction(ServiceExceptions.SelfActionException e) {
        return ResponseEntity.badRequest().body(new Dtos.MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(ServiceExceptions.NotFoundException.class)
    public ResponseEntity<Dtos.MessageResponse> handleNotFound(ServiceExceptions.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Dtos.MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Dtos.MessageResponse> handleBeanValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Invalid request.";
        }
        return ResponseEntity.badRequest().body(new Dtos.MessageResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Dtos.MessageResponse> handleUnexpected(Exception e) {
        // Log server-side detail; return a generic message to the client (IM8 as-13).
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Dtos.MessageResponse("An unexpected error occurred."));
    }
}
