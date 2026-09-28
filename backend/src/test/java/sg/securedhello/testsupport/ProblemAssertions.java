package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ErrorContract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asserts that a response is the shared error envelope for one code (ADR-031, T-AUTH-011): the code's status,
 * {@code application/problem+json}, a body that validates against the schema generated from {@link ErrorCode}, no
 * {@code BasicErrorController} members and no {@code WWW-Authenticate} challenge.
 */
public final class ProblemAssertions {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProblemAssertions() {
    }

    /** A MockMvc matcher: {@code mockMvc.perform(...).andExpect(problem(ErrorCode.ACCESS_DENIED))}. */
    public static ResultMatcher problem(ErrorCode expected) {
        return result -> assertProblem(result.getResponse().getStatus(), result.getResponse().getContentType(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE), expected);
    }

    /** The same checks on a raw response, for real-port tests. Returns the parsed body for further assertions. */
    public static JsonNode assertProblem(int status, @Nullable String contentType, String body,
            @Nullable String wwwAuthenticate, ErrorCode expected) {
        assertThat(status).as("status for %s", expected).isEqualTo(expected.status());
        assertThat(contentType).as("content type").isNotNull();
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("content type %s is application/problem+json", contentType).isTrue();
        assertThat(wwwAuthenticate).as("WWW-Authenticate (R-AUTH-005)").isNull();
        assertThat(ErrorContract.violations(body)).as("schema violations in %s", body).isEmpty();

        JsonNode json = JSON.readTree(body);
        assertThat(json.propertyNames()).as("BasicErrorController members")
                .doesNotContain("timestamp", "error", "message", "path");
        assertThat(json.get("code").asString()).isEqualTo(expected.name());
        return json;
    }
}
