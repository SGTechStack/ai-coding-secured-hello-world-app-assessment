package sg.securedhello.mfa;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.mfa.TotpEnrolment.FactorAlreadyEnrolledException;
import sg.securedhello.mfa.TotpEnrolment.InvalidFactorException;
import sg.securedhello.mfa.TotpVerification.FactorDisabledException;
import sg.securedhello.mfa.TotpVerification.FactorEnrolmentRequiredException;
import sg.securedhello.mfa.TotpVerification.FactorLockedException;

/**
 * Writes the factor refusals of the enrolment and verification routes (R-MFA-001; R-MFA-002; R-MFA-006). Ordered
 * first, so the catch-all {@code ProblemExceptionHandler} never sees them.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TotpProblemAdvice {

    /**
     * The {@code reason} of a tier-1 factor lock's 429. The {@code factor} member is what tells it from a source
     * throttle's 429, which never carries one (ADR-033).
     */
    public static final String LOCKED = "LOCKED";

    private final ProblemDetailWriter writer;

    public TotpProblemAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(FactorAlreadyEnrolledException.class)
    void alreadyEnrolled(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.FACTOR_ALREADY_ENROLLED);
    }

    @ExceptionHandler(InvalidFactorException.class)
    void invalidFactor(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.INVALID_FACTOR);
    }

    @ExceptionHandler(FactorEnrolmentRequiredException.class)
    void enrolmentRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.FACTOR_ENROLMENT_REQUIRED);
    }

    @ExceptionHandler(FactorDisabledException.class)
    void disabled(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.FACTOR_DISABLED);
    }

    @ExceptionHandler(FactorLockedException.class)
    void locked(FactorLockedException ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()));
        writer.write(request, response, ErrorCode.TOO_MANY_REQUESTS,
                Map.of("factor", TotpFactorEntryPoint.FACTOR, "reason", LOCKED));
    }
}
