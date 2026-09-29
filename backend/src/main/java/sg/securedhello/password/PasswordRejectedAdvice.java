package sg.securedhello.password;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Writes a {@link PasswordRejectedException} as 400 {@code PASSWORD_REJECTED} with its {@code rule}, for every
 * endpoint that sets a password. Ordered first, so the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PasswordRejectedAdvice {

    /** The extension member naming the failed rule. */
    public static final String RULE = "rule";

    private final ProblemDetailWriter writer;

    public PasswordRejectedAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(PasswordRejectedException.class)
    void rejected(PasswordRejectedException ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        writer.write(request, response, ErrorCode.PASSWORD_REJECTED, Map.of(RULE, ex.rule().name()));
    }
}
