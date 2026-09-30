package sg.example.helloauth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.everything;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.RecordingEmailService.Kind;
import sg.example.helloauth.support.TestAccount;

class PasswordResetTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static final String NEW_PASSWORD = "a-brand-new-password-1";
    private static final String ANOTHER_NEW_PASSWORD = "a-brand-new-password-2";

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    @RegisterExtension
    final LogCapture application = LogCapture.application();

    private Browser visitor() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser;
    }

    /** Requests a reset for this email and lets the background work run. */
    private void requestReset(String email) {
        visitor().requestPasswordReset(email).assertThat().hasStatus(202);
        backgroundTasks.runAll();
    }

    /** Requests a reset for Alice and returns the token in the link she is sent. */
    private String resetTokenForAlice() {
        requestReset(ALICE.email());
        return tokenSentTo(ALICE.email());
    }

    /** The token in the most recent reset link sent to this address. */
    private String tokenSentTo(String email) {
        String link = emails.sent(Kind.PASSWORD_RESET).stream()
                .filter(sent -> sent.to().address().equals(email))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No reset link was sent to " + email))
                .resetLink();
        return UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");
    }

    private MvcTestResult confirm(String token, String newPassword) {
        return visitor().confirmPasswordReset(token, newPassword);
    }

    private void assertPasswordIs(String password) {
        assertThat(visitor().login(ALICE.username(), password)).hasStatusOk();
    }

    private void failLogins(Browser browser, int times) {
        for (int i = 0; i < times; i++) {
            assertProblem(browser.login(ALICE.username(), "wrong-password-" + i), 401, "invalid credentials");
        }
    }

    private int tokenCount() {
        return jdbc.sql("SELECT COUNT(*) FROM password_reset_tokens").query(Integer.class).single();
    }

    private String aliceId() {
        return jdbc.sql("SELECT id FROM users WHERE username_key = 'testuser1'").query(String.class).single();
    }

    private static String sha256Hex(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ownerSetsANewPasswordThroughTheEmailedLink() {
        newBrowser().registered(ALICE);

        assertThat(confirm(resetTokenForAlice(), NEW_PASSWORD)).hasStatusOk();

        assertProblem(visitor().login(ALICE), 401, "invalid credentials");
        assertPasswordIs(NEW_PASSWORD);
    }

    @Test
    void requestIsAnsweredIdenticallyBeforeAnyWorkWhetherOrNotTheEmailIsRegistered() {
        newBrowser().registered(ALICE);

        MvcTestResult registered = visitor().requestPasswordReset(ALICE.email());
        MvcTestResult unregistered = visitor().requestPasswordReset("nobody@test.example.com");

        assertThat(registered).hasStatus(202);
        assertThat(unregistered).hasStatus(202);
        assertThat(registered.getResponse().getContentAsByteArray())
                .isEqualTo(unregistered.getResponse().getContentAsByteArray());
        // Nothing is looked up, stored or sent while the Visitor waits, so timing can't differ either.
        assertThat(emails.sent()).isEmpty();
        assertThat(tokenCount()).isZero();

        backgroundTasks.runAll();

        assertThat(emails.sent()).singleElement()
                .satisfies(email -> assertThat(email.to().address()).isEqualTo(ALICE.email()));
        assertThat(tokenCount()).isOne();
    }

    @Test
    void emailIsMatchedWhateverItsCase() {
        newBrowser().registered(ALICE);

        requestReset(ALICE.email().toUpperCase());

        assertThat(tokenSentTo(ALICE.email())).isNotBlank();
    }

    @Test
    void tokenIsAtLeast32RandomBytesStoredOnlyAsItsSha256HashForThirtyMinutes() throws Exception {
        newBrowser().registered(ALICE);

        String token = resetTokenForAlice();

        assertThat(Base64.getUrlDecoder().decode(token)).hasSizeGreaterThanOrEqualTo(32);
        Map<String, Object> stored = jdbc.sql("SELECT * FROM password_reset_tokens").query().singleRow();
        assertThat(stored).containsEntry("TOKEN_HASH", sha256Hex(token))
                .containsEntry("USER_ID", aliceId())
                .containsEntry("USED_AT", null);
        assertThat(stored.values()).noneSatisfy(value -> assertThat(String.valueOf(value)).contains(token));
        assertThat(((Timestamp) stored.get("EXPIRES_AT")).toLocalDateTime())
                .isEqualTo(LocalDateTime.ofInstant(START.plus(Duration.ofMinutes(30)), ZoneOffset.UTC));
    }

    @Test
    void eachResetLinkCarriesADifferentToken() {
        newBrowser().registered(ALICE);

        String first = resetTokenForAlice();
        String second = resetTokenForAlice();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tokenWorksOnlyOnce() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();
        confirm(token, NEW_PASSWORD).assertThat().hasStatusOk();

        assertProblem(confirm(token, ANOTHER_NEW_PASSWORD), 400, "password reset token expired or invalid");

        assertPasswordIs(NEW_PASSWORD);
    }

    @Test
    void tokenStillWorksJustBeforeThirtyMinutes() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();

        clock.setInstant(START.plus(Duration.ofMinutes(30)).minusSeconds(1));

        assertThat(confirm(token, NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void tokenExpiresAfterThirtyMinutesAndThePasswordStaysUnchanged() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();

        clock.setInstant(START.plus(Duration.ofMinutes(30)));

        assertProblem(confirm(token, NEW_PASSWORD), 400, "password reset token expired or invalid");
        assertPasswordIs(ALICE.password());
    }

    @Test
    void newTokenInvalidatesThePendingOne() {
        newBrowser().registered(ALICE);
        String older = resetTokenForAlice();
        String newer = resetTokenForAlice();

        assertProblem(confirm(older, NEW_PASSWORD), 400, "password reset token expired or invalid");
        assertThat(confirm(newer, NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void unknownUsedAndExpiredTokensGetOneIdenticalError() {
        newBrowser().registered(ALICE);
        String used = resetTokenForAlice();
        confirm(used, NEW_PASSWORD).assertThat().hasStatusOk();
        String expired = resetTokenForAlice();
        clock.setInstant(START.plus(Duration.ofHours(1)));

        MvcTestResult unknown = confirm("not-a-token-anyone-was-sent", ANOTHER_NEW_PASSWORD);

        assertProblem(unknown, 400, "password reset token expired or invalid");
        for (MvcTestResult rejected : List.of(confirm(used, ANOTHER_NEW_PASSWORD), confirm(expired, ANOTHER_NEW_PASSWORD))) {
            assertThat(rejected.getResponse().getContentAsByteArray())
                    .isEqualTo(unknown.getResponse().getContentAsByteArray());
        }
        assertPasswordIs(NEW_PASSWORD);
    }

    @Test
    void newPasswordMustMeetThePasswordPolicyAndARejectedOneLeavesTheTokenUsable() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();

        assertProblem(confirm(token, "too-short"), 400, "validation failed")
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).isEqualTo("newPassword"));
        assertProblem(confirm(token, "passwordpassword"), 400, "validation failed");

        assertThat(confirm(token, NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void resetEndsEveryExistingSessionOfTheAccount() {
        Browser alice = newBrowser().registerAndLogin(ALICE);
        String token = resetTokenForAlice();

        confirm(token, NEW_PASSWORD).assertThat().hasStatusOk();

        assertProblem(alice.get("/hello"), 401, "unauthenticated");
    }

    @Test
    void resetLeavesALockInPlace() {
        Browser alice = newBrowser().registered(ALICE);
        failLogins(alice, 5);

        confirm(resetTokenForAlice(), NEW_PASSWORD).assertThat().hasStatusOk();

        assertProblem(visitor().login(ALICE.username(), NEW_PASSWORD), 401, "invalid credentials");
        clock.setInstant(START.plus(Duration.ofMinutes(20)));
        assertPasswordIs(NEW_PASSWORD);
    }

    @Test
    void resetLeavesTheFailedLoginCounterAsItWas() {
        Browser alice = newBrowser().registered(ALICE);
        failLogins(alice, 4);
        confirm(resetTokenForAlice(), NEW_PASSWORD).assertThat().hasStatusOk();

        failLogins(alice, 1);

        assertProblem(visitor().login(ALICE.username(), NEW_PASSWORD), 401, "invalid credentials");
    }

    @Test
    void ownerIsNotifiedThatThePasswordChanged() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();
        assertThat(emails.sent(Kind.PASSWORD_CHANGED)).isEmpty();

        confirm(token, NEW_PASSWORD).assertThat().hasStatusOk();

        assertThat(emails.sent(Kind.PASSWORD_CHANGED)).singleElement().satisfies(email -> {
            assertThat(email.to().address()).isEqualTo(ALICE.email());
            assertThat(email.to().accountId()).hasToString(aliceId());
        });
    }

    @Test
    void requestIsAuditedAtInfoWithoutAnyAccountIdentity() {
        newBrowser().registered(ALICE);

        requestReset(ALICE.email());
        requestReset("nobody@test.example.com");

        List<ILoggingEvent> requests = audit.withAction("password-reset").stream()
                .filter(event -> event.getFormattedMessage().equals("Password reset requested."))
                .toList();
        assertThat(requests).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(fields(event))
                    .containsEntry("event.outcome", "success")
                    .containsEntry("event.type", "[start]")
                    .containsEntry("url.path", basePath + "/password-reset/request")
                    .doesNotContainKey("user.id");
        });
        assertThat(fields(requests.get(0))).isEqualTo(fields(requests.get(1)));
    }

    @Test
    void tokenIssuedInTheBackgroundIsAuditedWithTheAccountIdAndTheRequestsCorrelationId() {
        newBrowser().registered(ALICE);
        Browser visitor = visitor();
        visitor.send(visitor.withCsrf(mvc.post().uri(basePath + "/password-reset/request")
                .header("X-Correlation-ID", "reset-request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ALICE.email() + "\"}"))).assertThat().hasStatus(202);

        backgroundTasks.runAll();

        List<ILoggingEvent> issued = audit.withAction("password-reset").stream()
                .filter(event -> event.getFormattedMessage().equals("Password reset token issued."))
                .toList();
        assertThat(issued).singleElement().satisfies(event -> {
            assertThat(fields(event)).containsEntry("user.id", aliceId()).containsEntry("event.type", "[creation]");
            assertThat(event.getMDCPropertyMap()).containsEntry("correlation.id", "reset-request-1");
        });
    }

    @Test
    void noTokenIsAuditedForAnUnregisteredEmail() {
        requestReset("nobody@test.example.com");

        assertThat(audit.withAction("password-reset")).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Password reset requested.");
    }

    @Test
    void completedResetIsAuditedAtInfoWithTheAccountId() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();
        int before = audit.withAction("password-reset").size();

        confirm(token, NEW_PASSWORD).assertThat().hasStatusOk();

        List<ILoggingEvent> events = audit.withAction("password-reset");
        assertThat(events).hasSize(before + 1);
        ILoggingEvent completed = events.getLast();
        assertThat(completed.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(completed))
                .containsEntry("event.outcome", "success")
                .containsEntry("event.type", "[change]")
                .containsEntry("user.id", aliceId());
    }

    @Test
    void rejectedTokenIsAuditedAtWarnWithoutAnyAccountIdentity() {
        newBrowser().registered(ALICE);

        confirm("not-a-token-anyone-was-sent", NEW_PASSWORD);

        ILoggingEvent rejected = audit.single("password-reset");
        assertThat(rejected.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(rejected))
                .containsEntry("event.outcome", "failure")
                .containsEntry("error_code", "400")
                .doesNotContainKey("user.id");
    }

    @Test
    void noLogCarriesTheTokenTheNewPasswordOrPersonalData() {
        newBrowser().registered(ALICE);
        String token = resetTokenForAlice();
        confirm(token, NEW_PASSWORD).assertThat().hasStatusOk();
        confirm(token, ANOTHER_NEW_PASSWORD);

        List<ILoggingEvent> logged = new ArrayList<>(audit.events());
        logged.addAll(application.events());
        // Requested, issued, completed, and the rejected second use.
        assertThat(audit.withAction("password-reset")).hasSize(4);
        assertThat(logged).allSatisfy(event -> assertThat(everything(event)).doesNotContain(
                token, NEW_PASSWORD, ANOTHER_NEW_PASSWORD, ALICE.username(), ALICE.email()));
    }
}
