package sg.securedhello.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Envelope producer 3 (ADR-031). {@code CsrfFilter} reports its refusals here too, so a missing, wrong or superseded
 * CSRF token gets 403 {@code CSRF_TOKEN_INVALID}. Every other refusal of a signed-in caller gets 403
 * {@code ACCESS_DENIED}.
 */
final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    ProblemAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        if (exception instanceof CsrfException csrfException) {
            csrfRejected(request, response, csrfException);
        } else {
            writer.write(request, response, ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * A CSRF refusal ({@code MissingCsrfTokenException} or {@code InvalidCsrfTokenException}).
     *
     * <p>Ticket 10 seam: emit the CSRF-rejection audit row here, with reason {@code CSRF_MISSING} or the invalid-token
     * reason (T-CSRF-008), once the audit emitter (ticket 08) is merged.
     */
    private void csrfRejected(HttpServletRequest request, HttpServletResponse response, CsrfException exception)
            throws IOException {
        writer.write(request, response, ErrorCode.CSRF_TOKEN_INVALID);
    }
}
