package com.eitri.registration;

import com.eitri.auth.AccountConflictException;
import com.eitri.auth.AccountService;
import com.eitri.auth.AccountView;
import com.eitri.auth.PasswordPolicy;
import com.eitri.auth.Role;
import com.eitri.config.ApiError;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-registration. New accounts are always enabled {@code USER}s; any other field in the
 * request (role, enabled, counters) is ignored. Registration does not log the visitor in.
 */
@RestController
class RegistrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationController.class);
    // A simple local@domain.tld shape; deliverability is proven only by the password reset email.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]+$");
    private static final int MAX_EMAIL_LENGTH = 254;

    private final AccountService accounts;

    RegistrationController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("${app.api.base-path}/auth/register")
    ResponseEntity<?> register(@RequestBody RegistrationRequest registration) {
        Optional<Invalid> invalid = firstInvalid(registration);
        if (invalid.isPresent()) {
            // Field names only: never the submitted values.
            LOGGER.atWarn()
                    .addKeyValue("validation.fields", List.of(invalid.get().field()))
                    .setMessage("Invalid registration request")
                    .log();
            return ResponseEntity.badRequest().body(new ApiError(invalid.get().message()));
        }

        try {
            AccountView account = accounts.create(
                    registration.username(), registration.email(), registration.password(), Role.USER);
            return ResponseEntity.status(HttpStatus.CREATED).body(account);
        } catch (AccountConflictException conflict) {
            String message = conflict.field() == AccountConflictException.Field.USERNAME
                    ? "Username is already taken"
                    : "Email is already registered";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(message));
        }
    }

    private static Optional<Invalid> firstInvalid(RegistrationRequest registration) {
        String username = registration.username();
        if (username == null || username.isEmpty()) {
            return Optional.of(new Invalid("username", "Username is required"));
        }
        if (!PasswordPolicy.USERNAME.matcher(username).matches()) {
            return Optional.of(new Invalid("username", "Username is invalid"));
        }
        String email = registration.email();
        if (email == null || email.isEmpty()) {
            return Optional.of(new Invalid("email", "Email is required"));
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            return Optional.of(new Invalid("email", "Email is invalid"));
        }
        return PasswordPolicy.violation(registration.password()).map(message -> new Invalid("password", message));
    }

    record RegistrationRequest(String username, String email, String password) {}

    private record Invalid(String field, String message) {}
}
