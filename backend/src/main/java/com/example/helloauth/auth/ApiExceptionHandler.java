package com.example.helloauth.auth;

import com.example.helloauth.admin.AdminException;
import com.example.helloauth.passwordreset.InvalidResetTokenException;
import com.example.helloauth.user.RegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 {@code application/problem+json} error responses (ratified in
 * ticket 07). Extending {@link ResponseEntityExceptionHandler} turns
 * framework failures — {@code @NotBlank}/{@code @Email} violations, malformed
 * JSON — into ProblemDetail bodies automatically; the handlers below add the
 * registration domain errors.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RegistrationException.DuplicateUsername.class)
    ProblemDetail duplicateUsername(RegistrationException.DuplicateUsername ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Username already taken");
        return problem;
    }

    @ExceptionHandler(RegistrationException.DuplicateEmail.class)
    ProblemDetail duplicateEmail(RegistrationException.DuplicateEmail ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler(RegistrationException.PasswordTooShort.class)
    ProblemDetail passwordTooShort(RegistrationException.PasswordTooShort ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Password too short");
        return problem;
    }

    /**
     * Unknown, spent, or expired reset tokens collapse to one generic 400 —
     * the token is a credential, so its state is not public information.
     */
    @ExceptionHandler(InvalidResetTokenException.class)
    ProblemDetail invalidResetToken(InvalidResetTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Password reset failed");
        return problem;
    }

    /**
     * Admin self-targeting is a 400 domain rejection (the wayfinder ratifies
     * 400-or-403): the request is invalid for this caller regardless of
     * their admin role — an admin must never disable, demote, or delete the
     * account they're acting from.
     */
    @ExceptionHandler(AdminException.SelfAction.class)
    ProblemDetail adminSelfAction(AdminException.SelfAction ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Cannot modify own account");
        return problem;
    }

    /** Admin mutations against a non-existent id → 404. */
    @ExceptionHandler(AdminException.UserNotFound.class)
    ProblemDetail adminUserNotFound(AdminException.UserNotFound ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("User not found");
        return problem;
    }

    /**
     * IP-throttle rejections are a rate-limit contract, not a credential
     * failure — 429 (not the enumeration-resistant 401). No Retry-After:
     * revealing when the window decays would help an attacker pace a spray.
     */
    @ExceptionHandler(LoginThrottledException.class)
    ProblemDetail loginThrottled(LoginThrottledException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }
}
