package sg.example.helloauth.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.everything;
import static sg.example.helloauth.support.LogCapture.fields;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.TestAccount;

class AuditTrailTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    /** What MockMvc reports as every request's remote address. */
    private static final String CLIENT_IP = "127.0.0.1";

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    @RegisterExtension
    final LogCapture application = LogCapture.application();

    @Value("${app.audit.ip-hash-secret}")
    private String ipHashSecret;

    private String aliceId() {
        return jdbc.sql("SELECT id FROM users WHERE username_key = 'testuser1'").query(String.class).single();
    }

    @Test
    void registrationIsAuditedAtInfoWithTheNewAccountsId() {
        newBrowser().registered(ALICE);

        ILoggingEvent event = audit.single("user-provisioning");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event))
                .containsEntry("event.outcome", "success")
                .containsEntry("event.category", "[process]")
                .containsEntry("user.id", aliceId());
    }

    @Test
    void registrationClashIsAuditedAsAFailureWithoutIdentity() {
        Browser browser = newBrowser().registered(ALICE);

        browser.register(ALICE).assertThat().hasStatus(400);

        List<ILoggingEvent> events = audit.withAction("user-provisioning");
        assertThat(events).hasSize(2);
        ILoggingEvent rejected = events.get(1);
        assertThat(rejected.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(rejected))
                .containsEntry("event.outcome", "failure")
                .containsEntry("error_code", "400")
                .doesNotContainKey("user.id");
    }

    @Test
    void loginSuccessIsAuditedAtInfoWithTheAccountId() {
        newBrowser().registered(ALICE).login(ALICE).assertThat().hasStatusOk();

        ILoggingEvent event = audit.single("user-authentication");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event))
                .containsEntry("event.outcome", "success")
                .containsEntry("auth.method", "password")
                .containsEntry("http.request.method", "POST")
                .containsEntry("url.path", basePath + "/login")
                .containsEntry("user.id", aliceId());
    }

    @Test
    void loginFailureIsAuditedAtWarnWithoutAnyAccountIdentity() {
        Browser browser = newBrowser().registered(ALICE);

        browser.login(ALICE.username(), "not-the-password");
        browser.login("nobody", "not-the-password");

        List<ILoggingEvent> failures = audit.withAction("user-authentication");
        assertThat(failures).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fields(event))
                    .containsEntry("event.outcome", "failure")
                    .containsEntry("error_code", "401")
                    .containsEntry("error_category", "cert/auth")
                    .containsEntry("error_follow_up_action", "false")
                    .containsEntry("url.path", basePath + "/login")
                    .doesNotContainKey("user.id");
            assertThat(event.getMDCPropertyMap()).doesNotContainKey("user.id");
        });
        assertThat(fields(failures.get(0))).isEqualTo(fields(failures.get(1)));
    }

    @Test
    void logoutIsAuditedAtInfoWithTheAccountId() {
        newBrowser().registerAndLogin(ALICE).logout().assertThat().hasStatusOk();

        ILoggingEvent event = audit.single("user-logout");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event))
                .containsEntry("event.outcome", "success")
                .containsEntry("user.id", aliceId());
    }

    @Test
    void clientIpIsLoggedOnlyAsAKeyedHash() throws Exception {
        newBrowser().registered(ALICE).login(ALICE);

        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(ipHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(hmac.doFinal(CLIENT_IP.getBytes(StandardCharsets.UTF_8)));
        assertThat(audit.events()).isNotEmpty().allSatisfy(event -> assertThat(fields(event))
                .containsEntry("source.ip_hash", expected)
                .doesNotContainKey("source.ip"));
    }

    @Test
    void noLogCarriesCredentialsTokensSessionIdsOrPersonalData() {
        Browser browser = newBrowser().registered(ALICE);
        String csrfBeforeLogin = browser.csrfToken();
        browser.login(ALICE.username(), "a-wrong-password-1");
        browser.login("nobody", "a-wrong-password-2");
        browser.login(ALICE).assertThat().hasStatusOk();
        browser.fetchCsrf();
        String csrfAfterLogin = browser.csrfToken();
        String sessionCookie = browser.sessionCookie().getValue();
        String sessionId = new String(Base64.getDecoder().decode(sessionCookie), StandardCharsets.UTF_8);
        browser.get("/hello");
        browser.logout();

        List<ILoggingEvent> logged = new ArrayList<>(audit.events());
        logged.addAll(application.events());
        assertThat(audit.events()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(logged).allSatisfy(event -> assertThat(everything(event)).doesNotContain(
                ALICE.username(), ALICE.email(), ALICE.password(), "nobody", "a-wrong-password",
                csrfBeforeLogin, csrfAfterLogin, sessionCookie, sessionId, CLIENT_IP));
    }
}
