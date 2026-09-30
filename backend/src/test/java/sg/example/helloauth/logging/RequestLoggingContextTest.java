package sg.example.helloauth.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.TestAccount;

class RequestLoggingContextTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static final String CORRELATION_ID = "X-Correlation-ID";

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    private void failLoginWithCorrelationId(Browser browser, String correlationId) {
        browser.send(browser.withCsrf(mvc.post().uri(basePath + "/login")
                .header(CORRELATION_ID, correlationId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("username", "nobody")
                .formField("password", "not-the-password")));
    }

    @Test
    void correlationIdFromTheRequestIsOnEveryLogLine() {
        Browser browser = newBrowser();
        browser.fetchCsrf();

        failLoginWithCorrelationId(browser, "spa-4f1c2a");

        assertThat(audit.events()).isNotEmpty().allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry("correlation.id", "spa-4f1c2a"));
    }

    @Test
    void correlationIdIsGeneratedWhenTheRequestHasNone() {
        Browser browser = newBrowser().registered(ALICE);
        browser.login(ALICE);
        browser.fetchCsrf();
        browser.login(ALICE);

        List<String> ids = audit.withAction("user-authentication").stream()
                .map(event -> event.getMDCPropertyMap().get("correlation.id"))
                .toList();
        assertThat(ids).hasSize(2).doesNotContainNull().doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[0-9a-f-]{36}"));
    }

    @Test
    void lineBreaksInTheCorrelationIdCannotForgeALogLine() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        String forged = "abc\r\n{\"log.level\":\"INFO\",\"message\":\"forged\"} next";

        failLoginWithCorrelationId(browser, forged);

        assertThat(audit.events()).isNotEmpty().allSatisfy(event ->
                assertThat(event.getMDCPropertyMap().get("correlation.id"))
                        .startsWith("abc{")
                        .doesNotContain("\r", "\n", " "));
    }

    @Test
    void everyLogLineCarriesATraceId() {
        newBrowser().registered(ALICE).login(ALICE);

        assertThat(audit.events()).isNotEmpty().allSatisfy(event ->
                assertThat(event.getMDCPropertyMap().get("traceId")).matches("[0-9a-f]{16,32}"));
    }

    /** The audit event names its Account itself; ECS allows only one user.id per line. */
    @Test
    void auditEventOfAnAuthenticatedRequestCarriesItsUserIdOnlyAsAField() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        String id = jdbc.sql("SELECT id FROM users").query(String.class).single();

        browser.logout();

        assertThat(audit.single("user-logout").getMDCPropertyMap()).doesNotContainKey("user.id");
        assertThat(LogCapture.fields(audit.single("user-logout"))).containsEntry("user.id", id);
    }

    @Test
    void requestContextIsClearedAfterTheRequest() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        browser.send(mvc.get().uri(basePath + "/hello").header(CORRELATION_ID, "spa-1"));

        assertThat(MDC.get("correlation.id")).isNull();
        assertThat(MDC.get("user.id")).isNull();
    }
}
