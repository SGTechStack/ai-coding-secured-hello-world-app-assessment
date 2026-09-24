package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.auth.ErrorResponse;
import com.sgtechstack.helloworldauthapp.auth.ReauthenticationRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates admin-mutation failures into clear, uniform error responses.
 */
@RestControllerAdvice(basePackages = "com.sgtechstack.helloworldauthapp.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Validation failed", details));
    }

    @ExceptionHandler(SelfActionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleSelfAction(SelfActionNotAllowedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    /**
     * 409, not 400. The request is well-formed and the caller is authorized;
     * what refuses it is the current state of the system — there is exactly one
     * enabled admin left. 409 says "retry once the state differs", which is
     * accurate and actionable: promote another admin, then repeat.
     */
    @ExceptionHandler(LastAdminProtectedException.class)
    public ResponseEntity<ErrorResponse> handleLastAdmin(LastAdminProtectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    /**
     * 403, not 401. A 401 would tell the browser the session is gone and send
     * the SPA back to the login screen, discarding the admin's context over
     * what is really a missing confirmation on one action. The session is
     * valid; this particular request is not authorized without the password.
     */
    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleReauthentication(ReauthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(PurposeRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePurposeRequired(PurposeRequiredException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(exception.getMessage()));
    }
}
