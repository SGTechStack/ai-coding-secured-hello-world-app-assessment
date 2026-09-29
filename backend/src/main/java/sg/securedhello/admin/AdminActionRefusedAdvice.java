package sg.securedhello.admin;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Writes an {@link AdminActionRefusedException}: a self-action is 403 {@code ACCESS_DENIED}, like any other refusal
 * of the caller's authority (REJ-050), and the two-admin invariant is 409 {@code TWO_ADMIN_INVARIANT}. Ordered
 * first, so the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminActionRefusedAdvice {

    private final ProblemDetailWriter writer;

    public AdminActionRefusedAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(AdminActionRefusedException.class)
    void refused(AdminActionRefusedException ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        writer.write(request, response, switch (ex.reason()) {
            case SELF_ACTION -> ErrorCode.ACCESS_DENIED;
            case TWO_ADMIN_INVARIANT -> ErrorCode.TWO_ADMIN_INVARIANT;
        });
    }
}
