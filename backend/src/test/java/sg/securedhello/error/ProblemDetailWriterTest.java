package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests of the one error writer (ADR-031). */
class ProblemDetailWriterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProblemDetailWriter writer = new ProblemDetailWriter(JSON, Tracer.NOOP);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private JsonNode body() throws Exception {
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void writesTheEnvelopeWithTheCodesStatusAndConstants() throws Exception {
        writer.write(request, response, ErrorCode.TOO_MANY_REQUESTS);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        JsonNode body = body();
        assertThat(body.propertyNames()).containsExactly(
                "type", "title", "status", "detail", "instance", "traceId", "code");
        assertThat(body.get("detail").asString()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.detail());
        assertThat(body.get("instance").asString()).isEqualTo("/api/hello");
        assertThat(body.get("traceId").asString()).matches("[0-9a-f]{32}");
        assertThat(ErrorContract.violations(response.getContentAsString(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void onTheErrorDispatchTheInstanceIsTheOriginalPath() throws Exception {
        request.setRequestURI("/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/original");

        writer.write(request, response, ErrorCode.INTERNAL_ERROR);

        assertThat(body().get("instance").asString()).isEqualTo("/api/original");
    }

    @Test
    void theTraceIdIsStableWithinARequest() throws Exception {
        writer.write(request, response, ErrorCode.ACCESS_DENIED);
        String first = body().get("traceId").asString();
        MockHttpServletResponse second = new MockHttpServletResponse();

        writer.write(request, second, ErrorCode.ACCESS_DENIED);

        assertThat(JSON.readTree(second.getContentAsString(StandardCharsets.UTF_8)).get("traceId").asString())
                .isEqualTo(first);
        assertThat(request.getAttribute(ProblemDetailWriter.TRACE_ID_ATTRIBUTE)).isEqualTo(first);
    }

    @Test
    void extensionsFollowTheEnvelopeButCannotReplaceIt() throws Exception {
        writer.write(request, response, ErrorCode.PASSWORD_REJECTED, Map.of("rule", "MIN_LENGTH"));
        assertThat(body().get("rule").asString()).isEqualTo("MIN_LENGTH");

        assertThatIllegalArgumentException().isThrownBy(() -> writer.write(request, new MockHttpServletResponse(),
                ErrorCode.ACCESS_DENIED, Map.of("detail", "leaked")));
    }

    @Test
    void anEarlierPartialBodyIsDiscarded() throws Exception {
        response.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));

        writer.write(request, response, ErrorCode.ACCESS_DENIED);

        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).startsWith("{\"type\"");
    }

    @Test
    void aCommittedResponseIsLeftAlone() throws Exception {
        response.setStatus(200);
        response.getOutputStream().write("done".getBytes(StandardCharsets.UTF_8));
        response.setCommitted(true);

        writer.write(request, response, ErrorCode.INTERNAL_ERROR);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("done");
    }
}
