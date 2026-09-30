package sg.example.helloauth.api;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 Problem Details bodies that always carry a stable {@code code}. */
public final class ProblemDetails {

    public static final String CODE = "code";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        return withCode(status, code.value(), detail);
    }

    /** A problem for an error that has no more specific code than its status. */
    public static ProblemDetail forStatus(HttpStatusCode status, String detail) {
        return withCode(status, defaultCode(status), detail);
    }

    /** The stable code for an error that has no more specific one. */
    public static String defaultCode(HttpStatusCode status) {
        ErrorCode code = switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            default -> null;
        };
        if (code != null) {
            return code.value();
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "error" : known.getReasonPhrase().toLowerCase(Locale.ROOT);
    }

    private static ProblemDetail withCode(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
