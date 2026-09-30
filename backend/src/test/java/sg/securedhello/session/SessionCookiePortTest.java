package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.Proves;

import tools.jackson.databind.json.JsonMapper;

/**
 * The session cookie as Tomcat really sends and parses it: the raw {@code Set-Cookie}, and duplicate cookies, which
 * MockMvc would not reproduce.
 */
class SessionCookiePortTest extends CtxPortTest {

    private static final String UNSAFE_PATH = "/api/profile/password";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditEmitter auditEmitter;

    /** One bootstrapped session: the raw {@code Set-Cookie} headers, the cookie value and the token. */
    private record Bootstrap(List<String> setCookies, String value, String token) {
    }

    private Bootstrap bootstrap() {
        EntityExchangeResult<String> result = restClient.get().uri(uri("/api/csrf")).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult();
        List<String> setCookies = result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String value = setCookies.getFirst().split(";", 2)[0].split("=", 2)[1];
        return new Bootstrap(setCookies, value, JSON.readTree(result.getResponseBody()).get("token").asString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** POSTs with the raw {@code Cookie} header and a token, and returns the error code the API answered with. */
    private ErrorCode post(String cookieHeader, String token) {
        EntityExchangeResult<String> result = restClient.post().uri(uri(UNSAFE_PATH))
                .header(HttpHeaders.COOKIE, cookieHeader).header("X-CSRF-TOKEN", token)
                .exchange().expectBody(String.class).returnResult();
        return ErrorCode.valueOf(JSON.readTree(result.getResponseBody()).get("code").asString());
    }

    /**
     * SameSite=Strict, not the governing standard's Lax, by decision: ADR-058. The standard's own cookie test asserts
     * Lax and fails here on purpose (R-SES-004).
     */
    @Test
    @Proves({"T-SES-002", "T-SES-031"})
    void theTokenFetchSetsExactlyOneSessionCookieWithTheRequiredAttributes() {
        List<String> setCookies = bootstrap().setCookies();

        assertThat(setCookies).hasSize(1);
        List<String> attributes = List.of(setCookies.getFirst().split(";\\s*"));
        List<String> names = attributes.stream().skip(1).map(a -> a.split("=", 2)[0].toLowerCase(Locale.ROOT))
                .toList();
        assertThat(attributes.getFirst()).startsWith("SESSION=");
        assertThat(attributes).contains("HttpOnly", "SameSite=Strict", "Path=/");
        assertThat(names).doesNotContain("domain", "max-age", "expires", "secure");
        assertThat(context.getBeansOfType(CookieSerializer.class)).hasSize(1);
    }

    @Test
    void onlyTheFirstSessionCookieIsHonoured() {
        Bootstrap a = bootstrap();
        Bootstrap b = bootstrap();
        String unknown = cookieValue(UUID.randomUUID().toString());

        assertThat(post("SESSION=" + a.value() + "; SESSION=" + b.value(), a.token()))
                .isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
        assertThat(post("SESSION=" + a.value() + "; SESSION=" + b.value(), b.token()))
                .isEqualTo(ErrorCode.CSRF_TOKEN_INVALID);
        // The default resolver would skip the unknown id and find A's session behind it.
        assertThat(post("SESSION=" + unknown + "; SESSION=" + a.value(), a.token()))
                .isEqualTo(ErrorCode.CSRF_TOKEN_INVALID);
    }

    @Test
    @Proves("T-SES-030")
    void fiftySessionCookiesCostOneSessionStoreQuery() {
        String cookies = IntStream.range(0, 50).mapToObj(i -> "SESSION=" + cookieValue(UUID.randomUUID().toString()))
                .collect(Collectors.joining("; "));

        jdbc.execute("SET QUERY_STATISTICS FALSE");
        jdbc.execute("SET QUERY_STATISTICS TRUE");
        try {
            restClient.get().uri(uri("/actuator/health")).header(HttpHeaders.COOKIE, cookies).exchange();
            Integer lookups = jdbc.queryForObject("SELECT COALESCE(SUM(EXECUTION_COUNT), 0)"
                    + " FROM INFORMATION_SCHEMA.QUERY_STATISTICS"
                    + " WHERE SQL_STATEMENT LIKE '%SPRING_SESSION_ATTRIBUTES%' AND SQL_STATEMENT LIKE 'SELECT%'",
                    Integer.class);
            assertThat(lookups).isEqualTo(1);
        } finally {
            jdbc.execute("SET QUERY_STATISTICS FALSE");
        }
    }

    @Test
    @Proves("T-AUD-038")
    void twoSessionCookiesWriteRow11WithTheDuplicateReason() {
        Bootstrap a = bootstrap();
        Bootstrap b = bootstrap();
        auditEmitter.closeKeyingWindow();
        try (AuditCapture audit = AuditCapture.start()) {
            restClient.get().uri(uri("/actuator/health"))
                    .header(HttpHeaders.COOKIE, "SESSION=" + a.value() + "; SESSION=" + b.value())
                    .exchange().expectStatus().isOk();
            auditEmitter.closeKeyingWindow();

            assertThat(audit.withMessage("Invalid session presented.")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "DUPLICATE_SESSION_COOKIE")
                    .containsEntry("event.count", 1)
                    .doesNotContainKey("user.id"));
        }
    }

    private static String cookieValue(String sessionId) {
        return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }
}
