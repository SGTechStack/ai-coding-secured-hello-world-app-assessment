package sg.securedhello.credential;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Writes a {@link CredentialTokenInvalidException} as 400 {@code RESET_TOKEN_INVALID}, for every endpoint that redeems
 * a credential token, and its audit row (row 20), which names no account. The redemption's transaction has already
 * rolled back. Ordered first, so the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CredentialTokenInvalidAdvice {

    private final ProblemDetailWriter writer;
    private final AuditEmitter audit;

    public CredentialTokenInvalidAdvice(ProblemDetailWriter writer, AuditEmitter audit) {
        this.writer = writer;
        this.audit = audit;
    }

    @ExceptionHandler(CredentialTokenInvalidException.class)
    void invalid(CredentialTokenInvalidException refused, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        audit.emit(AuditEvent.CREDENTIAL_TOKEN_REFUSED, AccountContext.tokenRefused(refused.reason()));
        writer.write(request, response, ErrorCode.RESET_TOKEN_INVALID);
    }
}
