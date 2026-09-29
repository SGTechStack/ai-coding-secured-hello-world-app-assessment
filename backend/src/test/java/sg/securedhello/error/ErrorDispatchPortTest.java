package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.web.filter.OncePerRequestFilter;

import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;

/**
 * T-AUTH-001 through real Tomcat: an exception that escapes the controller advice and the security handlers is caught
 * by the container, which makes the ERROR dispatch to {@code /error} through the security chain; the envelope comes
 * back, never Tomcat's error page, the exception message or a stack trace (ADR-031).
 *
 * <p>This class deliberately starts its own context: it imports a test-only servlet filter that runs ahead of the
 * security chain and throws, because no route in the application lets an exception escape the advice.
 */
@Import(ErrorDispatchPortTest.EscapingFilterConfig.class)
class ErrorDispatchPortTest extends CtxPortTest {

    static final String ESCAPING_PATH = "/api/probe/escape";

    private static final String ESCAPED_MESSAGE = "escaped-internal-state-4d19";

    @TestConfiguration(proxyBeanMethods = false)
    static class EscapingFilterConfig {

        /** Throws on the original request only; the ERROR dispatch is not filtered, so it reaches {@code /error}. */
        @Bean
        FilterRegistrationBean<OncePerRequestFilter> escapingFilter() {
            OncePerRequestFilter filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    throw new IllegalStateException(ESCAPED_MESSAGE);
                }
            };
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns(ESCAPING_PATH);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", ErrorEnvelopeTest.SPA_ACCEPT})
    @Proves("T-AUTH-001")
    void anEscapedExceptionReachesTheErrorDispatchAndIsTheEnvelopeWithoutItsMessageOrStack(String accept) {
        EntityExchangeResult<String> result = restClient.get()
                .uri(URI.create("http://localhost:" + port + ESCAPING_PATH))
                .header(HttpHeaders.ACCEPT, accept)
                .exchange()
                .expectBody(String.class)
                .returnResult();
        String body = result.getResponseBody();

        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), body,
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.INTERNAL_ERROR);
        assertThat(body)
                .contains("\"instance\":\"" + ESCAPING_PATH + "\"")
                .contains("\"detail\":\"" + ErrorCode.INTERNAL_ERROR.detail() + "\"")
                .doesNotContain(ESCAPED_MESSAGE)
                .doesNotContain("IllegalStateException")
                .doesNotContain("at sg.securedhello")
                .doesNotContainIgnoringCase("<html");
    }
}
