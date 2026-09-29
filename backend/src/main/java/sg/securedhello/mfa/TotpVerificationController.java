package sg.securedhello.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.user.SignedInUser;

/**
 * {@code POST /api/mfa/totp/verification} (ADR-021; REJ-070): an administrator holding the password factor sends a
 * code in the JSON body, and a correct one grants or renews {@code FACTOR_TOTP} and rotates the session id, then
 * answers 204. The account is the session's, never one named in the body (ADR-027). A wrong code or a replay is 412
 * {@code INVALID_FACTOR}, no enrolled factor is 422 {@code FACTOR_ENROLMENT_REQUIRED}, a tier-1 locked factor is 429
 * {@code TOO_MANY_REQUESTS} with the factor member, and a tier-2 disabled one is 423 {@code FACTOR_DISABLED}, all
 * written by {@link TotpProblemAdvice} (ADR-027). {@code ROLE_ADMIN} in the authorization matrix, outside {@code /api/admin/**}, with its
 * own per-source budget.
 */
@RestController
public class TotpVerificationController {

    private final TotpVerification verification;
    private final TotpFactorGrant factorGrant;

    public TotpVerificationController(TotpVerification verification, TotpFactorGrant factorGrant) {
        this.verification = verification;
        this.factorGrant = factorGrant;
    }

    @PostMapping("/api/mfa/totp/verification")
    public ResponseEntity<Void> verify(Authentication authentication, @Valid @RequestBody TotpCodeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        SignedInUser user = (SignedInUser) authentication.getPrincipal();
        verification.verify(user.id(), body.code());
        factorGrant.grant(authentication, request, response);
        return ResponseEntity.noContent().build();
    }
}
