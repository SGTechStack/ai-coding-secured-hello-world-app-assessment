package sg.example.helloauth.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.password.ValidPassword;

/** Registration never logs the Visitor in; they go on to the login page. */
@RestController
@RequestMapping("${app.api.base-path}")
class RegistrationController {

    private final AccountService accounts;
    private final AuditLogger audit;

    RegistrationController(AccountService accounts, AuditLogger audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    void register(@Valid @RequestBody RegistrationRequest registration, HttpServletRequest request) {
        Account account;
        try {
            account = accounts.register(registration.username(), registration.email(), registration.password());
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.USER_EXIST) {
                audit.registrationRejected(request);
            }
            throw ex;
        }
        audit.registered(account.getId(), request);
    }

    /**
     * Limits match the {@code users} columns, so no input can fail in the database instead. The
     * Bootstrap admin's settings are held to the same username and email rules.
     */
    record RegistrationRequest(
            @NotNull @Pattern(regexp = "[a-zA-Z0-9._-]{3,32}",
                    message = "must be 3 to 32 letters, digits, '.', '_' or '-'") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @ValidPassword String password) {
    }
}
