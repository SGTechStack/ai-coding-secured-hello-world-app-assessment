package sg.securedhello.registration;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.error.ValidationRule;

/**
 * Two-step self-registration (ADR-032), both anonymous and both behind their per-source budgets (ADR-010):
 * <ul>
 *   <li>{@code POST /api/register} takes {@code {username, email}} and answers the same empty 202 whatever the email
 *       address's state. Refusals are about the username only: 400 {@code VALIDATION_FAILED}, with rule
 *       {@code USERNAME_UNAVAILABLE} when it is taken.</li>
 *   <li>{@code POST /api/register/activate} takes {@code {token, password}} and answers 204. A token that does not
 *       redeem is 400 {@code RESET_TOKEN_INVALID}; a refused password is 400 {@code PASSWORD_REJECTED} with its
 *       {@code rule}, and the token stays redeemable.</li>
 * </ul>
 * Neither creates or touches a session.
 */
@RestController
public class RegistrationController {

    /** The registration body. Both members are required; no other member is read. */
    public record RegistrationRequest(@NotNull String username, @NotNull String email) {
    }

    /** The activation body. Both members are required; no other member is read. */
    public record ActivationRequest(@NotNull String token, @NotNull String password) {
    }

    private final Registration registration;
    private final Activation activation;
    private final ProblemDetailWriter writer;

    public RegistrationController(Registration registration, Activation activation, ProblemDetailWriter writer) {
        this.registration = registration;
        this.activation = activation;
        this.writer = writer;
    }

    @PostMapping("/api/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest body) {
        registration.register(body.username(), body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/register/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivationRequest body) {
        activation.activate(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Registration.InvalidIdentifierException.class)
    void invalidIdentifier(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(Registration.UsernameUnavailableException.class)
    void usernameUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.VALIDATION_FAILED,
                Map.of("rule", ValidationRule.USERNAME_UNAVAILABLE.name()));
    }
}
