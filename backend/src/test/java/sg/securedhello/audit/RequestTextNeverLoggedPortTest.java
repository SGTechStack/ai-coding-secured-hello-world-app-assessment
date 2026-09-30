package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;

import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.Proves;

/**
 * What a caller sends is never written as sent (level P: through Tomcat, where the raw request line lives): not the
 * client address, not the query string, not credentials in headers, not an inbound trace header, and not line breaks
 * or an unbounded path in the one place raw request text reaches the audit stream, a pre-handler row's
 * {@code url.path}.
 * The CSRF refusal of an unsafe request to an unmatched path is that pre-handler row here; it is keyed by source, so
 * each test closes the keying window to read it.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestTextNeverLoggedPortTest extends CtxPortTest {

    private static final String CSRF_ROW = "CSRF validation failed.";

    /** Every textual form of the loopback peer this test's requests come from. */
    private static final List<String> LOOPBACK_FORMS = List.of("127.0.0.1", "0:0:0:0:0:0:0:1", "[::1]", "7f000001",
            "7f00:0001");

    @Autowired
    private AuditEmitter emitter;

    @Autowired
    private Environment environment;

    @BeforeEach
    void startAFreshWindow() {
        emitter.closeKeyingWindow();
    }

    private HttpResponse<String> send(String method, String rawPath, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + rawPath))
                .method(method, HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::header);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), BodyHandlers.ofString());
        }
    }

    /** The CSRF rows written for requests whose raw path starts with {@code prefix}, once the window closes. */
    private List<Map<String, Object>> csrfRowsFor(AuditCapture audit, String prefix) {
        emitter.closeKeyingWindow();
        return audit.withMessage(CSRF_ROW).stream()
                .filter(row -> String.valueOf(row.get("url.path")).startsWith(prefix)).toList();
    }

    @Test
    @Proves("T-AUD-006")
    void theClientAddressTheQueryStringAndAnAuthorizationHeaderAreNeverLogged(CapturedOutput output)
            throws Exception {
        String queryCanary = "query-canary-8d1e";
        String headerCanary = "authorization-canary-8d1e";
        int mark = output.getAll().length();

        try (AuditCapture audit = AuditCapture.start()) {
            assertThat(send("POST", "/api/aud006-unmatched?q=" + queryCanary,
                    Map.of("Authorization", "Bearer " + headerCanary)).statusCode()).isEqualTo(403);
            assertThat(send("GET", "/api/profile?q=" + queryCanary, Map.of("Authorization", "Basic " + headerCanary))
                    .statusCode()).isEqualTo(401);

            assertThat(csrfRowsFor(audit, "/api/aud006-unmatched")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("url.path", "/api/aud006-unmatched")
                    .containsKey("source.ip_hash")
                    .doesNotContainKeys("source.ip", "source.address", "client.ip", "client.address"));
        }

        String written = output.getAll().substring(mark);
        assertThat(written).doesNotContain(queryCanary, headerCanary);
        assertThat(written).doesNotContain(LOOPBACK_FORMS);
    }

    @Test
    @Proves("T-AUD-023")
    void lineBreaksAndPipesInARequestNeverReachTheAuditRowAsSent(CapturedOutput output) throws Exception {
        int mark = output.getAll().length();

        try (AuditCapture audit = AuditCapture.start()) {
            // Encoded line breaks are refused before any handler, by the firewall, and write no row at all.
            assertThat(send("POST", "/api/aud023%0D%0Ainjected-row", Map.of()).statusCode()).isEqualTo(400);
            // An encoded pipe passes the firewall: the row carries the path as the client encoded it, never a raw pipe.
            assertThat(send("POST", "/api/aud023%7Cpiped", Map.of()).statusCode()).isEqualTo(403);

            List<Map<String, Object>> rows = csrfRowsFor(audit, "/api/aud023");
            assertThat(rows).singleElement().satisfies(row -> assertThat((String) row.get("url.path"))
                    .isEqualTo("/api/aud023%7Cpiped").doesNotContain("\r", "\n", "|"));
        }

        String written = output.getAll().substring(mark);
        assertThat(written.lines()).noneMatch(line -> line.startsWith("injected-row"));
        assertThat(written).doesNotContain("\rinjected-row", "\ninjected-row");
    }

    @Test
    @Proves("T-AUD-024")
    void aSevenKilobytePathIsCappedAtTheConfiguredLengthWithTheMarker() throws Exception {
        int limit = environment.getProperty("app.audit.url-path.max-length", Integer.class);
        String longPath = "/api/aud024-" + "a".repeat(7 * 1024);

        try (AuditCapture audit = AuditCapture.start()) {
            assertThat(send("POST", longPath, Map.of()).statusCode()).isEqualTo(403);

            assertThat(csrfRowsFor(audit, "/api/aud024-")).singleElement().satisfies(row -> assertThat(
                    (String) row.get("url.path")).hasSize(limit).startsWith("/api/aud024-aaaa")
                    .endsWith(AuditText.TRUNCATION_MARKER).doesNotContain("\r", "\n", "|"));
        }
        assertThat(limit).isEqualTo(256);
    }

    @Test
    @Proves("T-AUD-028")
    void anInboundTraceHeaderIsNeverLoggedWithTheAccessLogOff(CapturedOutput output) throws Exception {
        assertThat(environment.getProperty("server.tomcat.accesslog.enabled", Boolean.class, false)).isFalse();
        String traceId = "5ca1ab1e5ca1ab1e5ca1ab1e5ca1ab1e";
        String traceparent = "00-" + traceId + "-5ca1ab1e5ca1ab1e-01";
        int mark = output.getAll().length();

        send("GET", "/api/hello", Map.of("traceparent", traceparent, "tracestate", "vendor=5ca1ab1e-state",
                "b3", traceId + "-5ca1ab1e5ca1ab1e-1", "X-B3-TraceId", traceId));
        send("POST", "/api/aud028-unmatched", Map.of("traceparent", traceparent));
        emitter.closeKeyingWindow();

        String written = output.getAll().substring(mark);
        assertThat(written).isNotEmpty().doesNotContain(traceId, "5ca1ab1e-state", "5ca1ab1e5ca1ab1e");
    }
}
