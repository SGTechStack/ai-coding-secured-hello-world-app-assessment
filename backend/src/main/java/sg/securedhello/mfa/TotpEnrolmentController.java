package sg.securedhello.mfa;

import java.io.IOException;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.mfa.TotpEnrolment.FactorAlreadyEnrolledException;
import sg.securedhello.mfa.TotpEnrolment.InvalidFactorException;
import sg.securedhello.mfa.TotpEnrolment.Provisioning;
import sg.securedhello.user.SignedInUser;

/**
 * TOTP enrolment for an administrator with no factor (ADR-023). Both routes are {@code ROLE_ADMIN} in the authorization
 * matrix, outside {@code /api/admin/**}, so an unenrolled admin reaches them, and off the forced-change allowlist, so
 * the password change comes first. Their per-source budgets run before any of this.
 *
 * <ul>
 *   <li>{@code POST /api/mfa/totp/enrolment} answers 200 with {@code otpauthUri}, {@code secretBase32} and
 *       {@code qrPng} (Base64 PNG), sent once with {@code Cache-Control: no-store} (ADR-025; R-FE-007). A POST, not
 *       the standard's GET, for CSRF (REJ-069). 409 {@code FACTOR_ALREADY_ENROLLED} when a factor exists.</li>
 *   <li>{@code POST /api/mfa/totp/enrolment/confirmation} takes {@code {"code": "123456"}} in the body (REJ-070),
 *       binds the factor, grants {@code FACTOR_TOTP} and rotates the session id, then answers 204 (REJ-071). A wrong
 *       code, or no pending enrolment, is 412 {@code INVALID_FACTOR}.</li>
 * </ul>
 */
@RestController
public class TotpEnrolmentController {

    /** The provisioning body. Never logged: the URI and the Base32 string both carry the secret. */
    public record ProvisioningResponse(String otpauthUri, String secretBase32, String qrPng) {

        @Override
        public String toString() {
            return "ProvisioningResponse[<redacted>]";
        }
    }

    /** The confirmation body. */
    public record ConfirmationRequest(@NotNull String code) {

        @Override
        public String toString() {
            return "ConfirmationRequest[<redacted>]";
        }
    }

    private final TotpEnrolment enrolment;
    private final TotpFactorGrant factorGrant;
    private final ProblemDetailWriter writer;

    public TotpEnrolmentController(TotpEnrolment enrolment, TotpFactorGrant factorGrant, ProblemDetailWriter writer) {
        this.enrolment = enrolment;
        this.factorGrant = factorGrant;
        this.writer = writer;
    }

    @PostMapping("/api/mfa/totp/enrolment")
    public ResponseEntity<ProvisioningResponse> provision(Authentication authentication) {
        SignedInUser user = (SignedInUser) authentication.getPrincipal();
        Provisioning provisioning = enrolment.provision(user.id(), user.getUsername());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProvisioningResponse(provisioning.otpauthUri(), provisioning.secretBase32(),
                        Base64.getEncoder().encodeToString(provisioning.qrPng())));
    }

    @PostMapping("/api/mfa/totp/enrolment/confirmation")
    public ResponseEntity<Void> confirm(Authentication authentication, @Valid @RequestBody ConfirmationRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        SignedInUser user = (SignedInUser) authentication.getPrincipal();
        enrolment.confirm(user.id(), body.code());
        factorGrant.grant(authentication, request, response);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(FactorAlreadyEnrolledException.class)
    void alreadyEnrolled(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.FACTOR_ALREADY_ENROLLED);
    }

    @ExceptionHandler(InvalidFactorException.class)
    void invalidFactor(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.INVALID_FACTOR);
    }
}
