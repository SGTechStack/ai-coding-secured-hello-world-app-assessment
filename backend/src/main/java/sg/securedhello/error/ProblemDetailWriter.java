package sg.securedhello.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * The only code that writes an error body (ADR-031). Every component that can end a request with an error injects
 * this writer: the controller advice, the {@code /error} dispatch, the security entry point and denied handlers, and
 * the application's own filters. {@code HttpServletResponse.sendError} is prohibited, because it hands the body to
 * the container's error page instead.
 *
 * <p>The body is RFC 9457 {@code application/problem+json}: {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance}, {@code traceId} and {@code code}, plus any extension members a producer passes.
 * It is written whatever the request's {@code Accept} header says, so no error falls through to a 406.
 */
@Component
public class ProblemDetailWriter {

    /** Request attribute holding the request's trace id, so every write within one request reports the same one. */
    public static final String TRACE_ID_ATTRIBUTE = ProblemDetailWriter.class.getName() + ".traceId";

    private static final Set<String> ENVELOPE_MEMBERS =
            Set.of("type", "title", "status", "detail", "instance", "traceId", "code");

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailWriter.class);

    private final JsonMapper jsonMapper;

    public ProblemDetailWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Writes the envelope for {@code code}, with no extension members. */
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        write(request, response, code, Map.of());
    }

    /**
     * Writes the envelope for {@code code} plus {@code extensions}, such as a password rule. An extension may not
     * replace an envelope member.
     *
     * @throws IllegalArgumentException if an extension name is an envelope member
     */
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
            Map<String, ?> extensions) throws IOException {
        for (String name : extensions.keySet()) {
            if (ENVELOPE_MEMBERS.contains(name)) {
                throw new IllegalArgumentException("extension '" + name + "' would replace an envelope member");
            }
        }
        if (response.isCommitted()) {
            log.warn("Response already committed; error code {} not written", code);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.type());
        body.put("title", code.title());
        body.put("status", code.status());
        body.put("detail", code.detail());
        body.put("instance", instance(request));
        body.put("traceId", traceId(request));
        body.put("code", code.name());
        body.putAll(extensions);

        byte[] bytes = jsonMapper.writeValueAsBytes(body);
        response.resetBuffer();
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    /** The path that failed; on the {@code /error} dispatch, the original request's path rather than {@code /error}. */
    private static String instance(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                ? original
                : request.getRequestURI();
    }

    /**
     * A 32-hex-digit id, shaped like a W3C trace id, kept for the rest of the request. Until tracing is wired in
     * (ADR-063) nothing else sets the attribute, so the writer mints one.
     */
    private static String traceId(HttpServletRequest request) {
        if (request.getAttribute(TRACE_ID_ATTRIBUTE) instanceof String existing) {
            return existing;
        }
        String minted = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ID_ATTRIBUTE, minted);
        return minted;
    }
}
