package sg.example.helloauth.api;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/** A Throttled request: 429 {@code too many requests}, with a {@code Retry-After} header. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        this.retryAfter = retryAfter;
    }

    /** The wait in whole seconds, rounded up and at least one, as {@code Retry-After} needs. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
