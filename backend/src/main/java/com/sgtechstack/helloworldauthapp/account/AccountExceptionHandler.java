package com.sgtechstack.helloworldauthapp.account;

import com.sgtechstack.helloworldauthapp.admin.LastAdminProtectedException;
import com.sgtechstack.helloworldauthapp.auth.ErrorResponse;
import com.sgtechstack.helloworldauthapp.auth.ReauthenticationRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates self-service account failures into the same uniform error shape
 * the rest of the API uses.
 *
 * <p>Package-scoped advice, matching the existing convention: three sibling
 * advices cover auth, admin and password reset, each bound to its own package.
 * A single global advice would be less code and would also quietly change
 * behaviour for every controller at once, which is not a trade this application
 * has chosen to make elsewhere.
 *
 * <p>The exception types deliberately come from other packages. The re-auth
 * requirement and the last-admin invariant are the same rules with the same
 * meanings as on the admin path; duplicating them per package would be two
 * chances for the two copies to answer differently.
 */
@RestControllerAdvice(basePackages = "com.sgtechstack.helloworldauthapp.account")
public class AccountExceptionHandler {

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleReauthentication(ReauthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    /**
     * 409: the request is valid and authorized, but the system's current state
     * refuses it. The sole remaining administrator cannot erase themselves;
     * promoting somebody else first makes the same request succeed.
     */
    @ExceptionHandler(LastAdminProtectedException.class)
    public ResponseEntity<ErrorResponse> handleLastAdmin(LastAdminProtectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    /**
     * A session whose account has since been deleted. 401 rather than 404: the
     * useful thing to tell the client is that its credentials are no longer
     * good, so the SPA returns to the login screen instead of showing an error
     * beside a signed-in shell that no longer has an account behind it.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(exception.getMessage()));
    }
}
