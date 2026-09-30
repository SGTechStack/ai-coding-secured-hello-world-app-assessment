package sg.example.helloauth.passwordreset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;
import sg.example.helloauth.api.TooManyRequestsException;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.loginprotection.Throttling;
import sg.example.helloauth.password.ValidPassword;

@RestController
@RequestMapping("${app.api.base-path}/password-reset")
class PasswordResetController {

    /** The one answer to every reset request, registered email or not. */
    static final MessageResponse REQUEST_ACCEPTED = new MessageResponse(
            "If that email is registered, a password reset link has been sent to it.");

    private final PasswordResetService passwordReset;
    private final Throttling throttling;
    private final AuditLogger audit;

    PasswordResetController(PasswordResetService passwordReset, Throttling throttling, AuditLogger audit) {
        this.passwordReset = passwordReset;
        this.throttling = throttling;
        this.audit = audit;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse request(@Valid @RequestBody ResetRequest reset, HttpServletRequest request) {
        throttling.admitPasswordResetEmail(reset.email(), request).ifPresent(wait -> {
            throw new TooManyRequestsException(wait);
        });
        passwordReset.requestReset(reset.email());
        audit.passwordResetRequested(request);
        return REQUEST_ACCEPTED;
    }

    @PostMapping("/confirm")
    void confirm(@Valid @RequestBody ResetConfirmation confirmation, HttpServletRequest request) {
        Account account;
        try {
            account = passwordReset.confirmReset(confirmation.token(), confirmation.newPassword());
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.PASSWORD_RESET_TOKEN_INVALID) {
                audit.passwordResetRejected(request);
            }
            throw ex;
        }
        audit.passwordResetCompleted(account.getId(), request);
    }

    record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** A token is 43 characters; anything much longer can't be one. */
    record ResetConfirmation(@NotBlank @Size(max = 128) String token, @NotNull @ValidPassword String newPassword) {
    }

    record MessageResponse(String message) {
    }
}
