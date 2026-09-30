package sg.securedhello.error;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Envelope producer 1 (ADR-031): every exception raised in Spring MVC. Framework exceptions are mapped by status
 * through {@link ErrorCode#forStatus(int)}; anything else is {@link ErrorCode#INTERNAL_ERROR}, and its message never
 * reaches the body.
 *
 * <p>Each handler writes through {@link ProblemDetailWriter} and returns {@code null}, which tells Spring MVC the
 * response is complete, so content negotiation never gets a chance to turn the error into a 406.
 */
@RestControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    private final ProblemDetailWriter writer;

    public ProblemExceptionHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    /**
     * Security exceptions from method security belong to the filter chain's entry point and denied handler.
     * Rethrowing the same instance hands them back to {@code ExceptionTranslationFilter}.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, NativeWebRequest request) throws IOException {
        ErrorLog.unhandled(log, "Unhandled exception", ex);
        return write(request, ErrorCode.INTERNAL_ERROR);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.forStatus(statusCode.value());
        log.debug("{} mapped to {}", ex.getClass().getName(), code);
        try {
            return write((NativeWebRequest) request, code);
        } catch (IOException writeFailure) {
            log.warn("Could not write the error body for {}", code, writeFailure);
            return null;
        }
    }

    private @Nullable ResponseEntity<Object> write(NativeWebRequest request, ErrorCode code) throws IOException {
        writer.write(request.getNativeRequest(HttpServletRequest.class),
                request.getNativeResponse(HttpServletResponse.class), code);
        return null;
    }
}
