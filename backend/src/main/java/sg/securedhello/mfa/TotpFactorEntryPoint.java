package sg.securedhello.mfa;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authorization.RequiredFactorError;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Component;

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
 *   <li>Otherwise 412 {@code MISSING_FACTOR}, with {@code factor: TOTP} and {@code reason} {@code MISSING} or
 *       {@code EXPIRED}, the latter when the session holds the factor but the rule's duration has passed
 *       (R-MFA-001).</li>
 * </ul>
 */
@Component
public class TotpFactorEntryPoint implements AuthenticationEntryPoint {

    /** The {@code factor} member's value. */
    public static final String FACTOR = "TOTP";

    /** Why the factor was refused, as the {@code reason} member. */
    public enum Reason {
        /** The session does not hold the factor. */
        MISSING,
        /** The session holds it, but it was issued longer ago than the rule accepts. */
        EXPIRED
    }

    private final TotpUserDetailsRepository factors;
    private final ProblemDetailWriter writer;

    TotpFactorEntryPoint(TotpUserDetailsRepository factors, ProblemDetailWriter writer) {
        this.factors = factors;
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user
                && !factors.existsById(user.id())) {
            writer.write(request, response, ErrorCode.FACTOR_ENROLMENT_REQUIRED);
            return;
        }
        writer.write(request, response, ErrorCode.MISSING_FACTOR,
                Map.of("factor", FACTOR, "reason", reason(request).name()));
    }

    /** {@code EXPIRED} when the framework reported the TOTP factor as expired, {@code MISSING} otherwise. */
    static Reason reason(HttpServletRequest request) {
        return request.getAttribute(WebAttributes.REQUIRED_FACTOR_ERRORS) instanceof List<?> errors
                && errors.stream().anyMatch(error -> error instanceof RequiredFactorError factorError
                        && TotpFactorGrant.AUTHORITY.equals(factorError.getRequiredFactor().getAuthority())
                        && factorError.isExpired())
                ? Reason.EXPIRED
                : Reason.MISSING;
    }
}
