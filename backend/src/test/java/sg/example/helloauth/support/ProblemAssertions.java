package sg.example.helloauth.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.MediaType;
import org.springframework.test.json.AbstractJsonContentAssert;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Assertions on the API's RFC 9457 Problem Details responses. */
public final class ProblemAssertions {

    private ProblemAssertions() {
    }

    /**
     * Asserts the response is a Problem Details body with this status and stable {@code code},
     * and returns the body for further checks.
     */
    public static AbstractJsonContentAssert<?> assertProblem(MvcTestResult result, int status, String code) {
        return assertThat(result).hasStatus(status)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", actual -> assertThat(actual).isEqualTo(code));
    }
}
