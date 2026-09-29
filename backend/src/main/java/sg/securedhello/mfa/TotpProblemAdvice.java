package sg.securedhello.mfa;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.mfa.TotpEnrolment.FactorAlreadyEnrolledException;
import sg.securedhello.mfa.TotpEnrolment.InvalidFactorException;
import sg.securedhello.mfa.TotpVerification.FactorDisabledException;
import sg.securedhello.mfa.TotpVerification.FactorEnrolmentRequiredException;

/**
 * Writes the factor refusals of the enrolment and verification routes (R-MFA-001; R-MFA-002). Ordered first, so the
 * catch-all {@code ProblemExceptionHandler} never sees them.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TotpProblemAdvice {

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
}
