package sg.securedhello.session.shedding;

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
 * Writes a shed request as 429 {@code TOO_MANY_REQUESTS} with a constant {@code Retry-After: 60} (ADR-041). The value
 * is constant because a computed one would reveal disk state, and 60 seconds is the dwell, so no episode clears
 * sooner. Ordered first, so the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NewSessionShedAdvice {

    /** The constant {@code Retry-After}, in seconds. */
    static final String RETRY_AFTER_SECONDS = Long.toString(ShedEpisode.DWELL.toSeconds());

    private final ProblemDetailWriter writer;

    public NewSessionShedAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(NewSessionShedException.class)
    void shed(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        writer.write(request, response, ErrorCode.TOO_MANY_REQUESTS);
    }
}
