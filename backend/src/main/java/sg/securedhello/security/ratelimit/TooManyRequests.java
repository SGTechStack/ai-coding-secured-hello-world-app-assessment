package sg.securedhello.security.ratelimit;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;

/** Every throttle's answer: 429 {@code TOO_MANY_REQUESTS} with an integer {@code Retry-After} (ADR-014; RFC 9110). */
public final class TooManyRequests {

    private TooManyRequests() {
    }

    /** Writes {@code refusal} as the envelope, with its {@code Retry-After} in whole seconds. */
    public static void write(ProblemDetailWriter writer, HttpServletRequest request, HttpServletResponse response,
            Refusal refusal) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(refusal.retryAfterSeconds()));
        writer.write(request, response, ErrorCode.TOO_MANY_REQUESTS);
    }
}
