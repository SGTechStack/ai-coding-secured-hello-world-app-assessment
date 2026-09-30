package sg.securedhello.admin;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Writes an {@link AdminActionRefusedException}: a self-action is 403 {@code ACCESS_DENIED}, like any other refusal
 * of the caller's authority (REJ-050), the two-admin invariant is 409 {@code TWO_ADMIN_INVARIANT}, and a lock set
 * that timed out under contention is 503 {@code SERVICE_BUSY} with an integer {@code Retry-After}. Ordered first, so
 * the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminActionRefusedAdvice {

    /** {@code Retry-After} on {@code SERVICE_BUSY}, in seconds: a guarded change holds its locks for milliseconds. */
    static final String BUSY_RETRY_AFTER_SECONDS = "1";

    private final ProblemDetailWriter writer;

    public AdminActionRefusedAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(AdminActionRefusedException.class)
    void refused(AdminActionRefusedException ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ErrorCode code = switch (ex.reason()) {
            case SELF_ACTION -> ErrorCode.ACCESS_DENIED;
            case TWO_ADMIN_INVARIANT -> ErrorCode.TWO_ADMIN_INVARIANT;
            case LOCK_TIMEOUT -> ErrorCode.SERVICE_BUSY;
        };
        if (code == ErrorCode.SERVICE_BUSY) {
            response.setHeader(HttpHeaders.RETRY_AFTER, BUSY_RETRY_AFTER_SECONDS);
        }
        writer.write(request, response, code);
    }
}
