package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;

/**
 * Through real Tomcat, where the container's own error page could appear: an unmatched route and a firewall rejection
 * still return the envelope (ADR-031).
 */
class ErrorEnvelopePortTest extends CtxPortTest {

    private void expectProblem(String path, String accept, ErrorCode code) {
        EntityExchangeResult<String> result = restClient.get()
                .uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, accept)
                .exchange()
                .expectBody(String.class)
                .returnResult();

        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), result.getResponseBody(),
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), code);
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", ErrorEnvelopeTest.SPA_ACCEPT})
    @Proves({"T-ADM-007", "T-AUTH-011"})
    void anUnmatchedRouteIsTheEnvelopeNeverAContainerErrorPage(String accept) {
        expectProblem("/no/such/route", accept, ErrorCode.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/hello;jsessionid=x", "/api/%2e%2e/hello", "/api//hello"})
    @Proves("T-AUTH-011")
    void aRequestTheFirewallRejectsIsTheEnvelope(String path) {
        expectProblem(path, ErrorEnvelopeTest.SPA_ACCEPT, ErrorCode.VALIDATION_FAILED);
    }
}
