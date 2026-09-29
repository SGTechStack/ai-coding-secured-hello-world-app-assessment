package sg.securedhello.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.CsrfReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.SignedInUser;

/**
 * Envelope producer 3 (ADR-031). {@code CsrfFilter} reports its refusals here too, so a missing, wrong or superseded
 * CSRF token gets 403 {@code CSRF_TOKEN_INVALID}. Every other refusal of a signed-in caller gets 403
 * {@code ACCESS_DENIED}.
 */
final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;
    private final AuditEmitter audit;

    ProblemAccessDeniedHandler(ProblemDetailWriter writer, AuditEmitter audit) {
        this.writer = writer;
        this.audit = audit;
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
     * A CSRF refusal, written to the audit stream as row 13 (Std §3.3, security control bypass attempts). The reason is
     * {@code CSRF_MISSING} when the session held no token to compare against ({@code MissingCsrfTokenException}),
     * which includes every request without a session (T-CSRF-008), and {@code CSRF_INVALID} otherwise.
     */
    private void csrfRejected(HttpServletRequest request, HttpServletResponse response, CsrfException exception)
            throws IOException {
        CsrfReason reason = exception instanceof MissingCsrfTokenException
                ? CsrfReason.CSRF_MISSING
                : CsrfReason.CSRF_INVALID;
        audit.emit(AuditEvent.CSRF_REJECTED, AccountContext.csrfRejected(signedInUserId(), reason));
        writer.write(request, response, ErrorCode.CSRF_TOKEN_INVALID);
    }

    private static UUID signedInUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SignedInUser user
                ? user.id()
                : null;
    }
}
