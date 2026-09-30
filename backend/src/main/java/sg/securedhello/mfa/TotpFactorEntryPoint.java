package sg.securedhello.mfa;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authorization.RequiredFactorError;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Component;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.FactorRequiredReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.SignedInUser;

/**
 * The entry point for a missing or expired {@code FACTOR_TOTP} (ADR-026). Only the password and one-time-token factors
 * get one from the framework, so without it the denial would be a bare 403. It is reached only after the role check
 * has passed, because the admin rules put the role first, so every caller here is a signed-in administrator and the
 * specific answer opens no enumeration channel (ADR-021; ADR-033).
 *
 * <ul>
 *   <li>No enrolled factor: 422 {@code FACTOR_ENROLMENT_REQUIRED} (R-MFA-002).</li>
 *   <li>A factor tier 2 disabled: 423 {@code FACTOR_DISABLED}, the terminal state, rather than a challenge no code can
 *       pass (R-MFA-006; REJ-049).</li>
 *   <li>Otherwise 412 {@code MISSING_FACTOR}, with {@code factor: TOTP} and {@code reason} {@code MISSING} or
 *       {@code EXPIRED}, the latter when the session holds the factor but the rule's duration has passed
 *       (R-MFA-001). A signed-in administrator's 412 writes row 14, a tier-2 keyed row (ADR-019).</li>
 * </ul>
 */
@Component
public class TotpFactorEntryPoint implements AuthenticationEntryPoint {

    /** The {@code factor} member's value. */
    public static final String FACTOR = "TOTP";

    private final TotpUserDetailsRepository factors;
    private final ProblemDetailWriter writer;
    private final AuditEmitter audit;

    TotpFactorEntryPoint(TotpUserDetailsRepository factors, ProblemDetailWriter writer, AuditEmitter audit) {
        this.factors = factors;
        this.writer = writer;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
            Optional<TotpUserDetails> factor = factors.findById(user.id());
            if (factor.isEmpty()) {
                writer.write(request, response, ErrorCode.FACTOR_ENROLMENT_REQUIRED);
                return;
            }
            if (factor.get().isDisabled()) {
                writer.write(request, response, ErrorCode.FACTOR_DISABLED);
                return;
            }
        }
        FactorRequiredReason reason = reason(request);
        if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
            audit.emit(AuditEvent.FACTOR_REQUIRED, AccountContext.factorRequired(user.id(), reason));
        }
        writer.write(request, response, ErrorCode.MISSING_FACTOR, Map.of("factor", FACTOR, "reason", reason.name()));
    }

    /** {@code EXPIRED} when the framework reported the TOTP factor as expired, {@code MISSING} otherwise. */
    static FactorRequiredReason reason(HttpServletRequest request) {
        return request.getAttribute(WebAttributes.REQUIRED_FACTOR_ERRORS) instanceof List<?> errors
                && errors.stream().anyMatch(error -> error instanceof RequiredFactorError factorError
                        && TotpFactorGrant.AUTHORITY.equals(factorError.getRequiredFactor().getAuthority())
                        && factorError.isExpired())
                ? FactorRequiredReason.EXPIRED
                : FactorRequiredReason.MISSING;
    }
}
