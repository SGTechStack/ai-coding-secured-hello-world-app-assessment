package sg.example.helloauth.loginprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.everything;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.TestAccount;

/**
 * Throttle state lives in memory for the life of the context, so every test here comes from
 * addresses and uses usernames no other test uses.
 */
@TestPropertySource(properties = {
        "app.login-protection.throttle.failed-logins-per-ip.attempts=3",
        "app.login-protection.throttle.failed-logins-per-ip.period=10m",
        "app.login-protection.throttle.logins-per-username.attempts=2",
        "app.login-protection.throttle.logins-per-username.period=1m",
        "app.login-protection.throttle.registrations-per-ip.attempts=2",
        "app.login-protection.throttle.registrations-per-ip.period=1h",
        "app.login-protection.throttle.password-reset-requests-per-ip.attempts=2",
        "app.login-protection.throttle.password-reset-requests-per-email.attempts=2",
        "app.login-protection.throttle.password-reset-confirms-per-ip.attempts=2",
        "app.api.trusted-proxies=" + ThrottlingTest.TRUSTED_PROXY})
class ThrottlingTest extends IntegrationTest {

    static final String TRUSTED_PROXY = "10.9.9.9";

    private static final AtomicInteger addresses = new AtomicInteger();
    private static final AtomicInteger accounts = new AtomicInteger(100);

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    private static String newAddress() {
        return "203.0.113." + addresses.incrementAndGet();
    }

    private static TestAccount newAccount() {
        return TestAccount.testUser(accounts.incrementAndGet());
    }

    /** A username no Account has, and no other test tries. */
    private static String newUsername() {
        return "unknown" + accounts.incrementAndGet();
    }

    /** A Visitor at an address of its own, holding a CSRF token. */
    private Browser newVisitor() {
        Browser browser = newBrowserAt(newAddress());
        browser.fetchCsrf();
        return browser;
    }

    private TestAccount registered() {
        TestAccount account = newAccount();
        newVisitor().register(account).assertThat().hasStatus(201);
        return account;
    }

    private MvcTestResult loginForwardedFor(Browser browser, String forwardedFor, String username) {
        return browser.send(browser.withCsrf(mvc.post().uri(basePath + "/login")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("username", username)
                .formField("password", "wrong-password")));
    }

    private static void assertThrottled(MvcTestResult result) {
        assertProblem(result, 429, "too many requests");
        assertThat(result.getResponse().getHeader("Retry-After")).matches("[1-9][0-9]*");
    }

    /** Uses up a username's two logins a minute, from an address of their own, and tries a third. */
    private MvcTestResult thirdLoginAs(String username) {
        Browser browser = newVisitor();
        for (int i = 0; i < 2; i++) {
            assertProblem(browser.login(username, "wrong-password"), 401, "invalid credentials");
        }
        return browser.login(username, "wrong-password");
    }

    @Test
    void failedLoginsFromOneAddressAreThrottledAcrossUsernamesWithoutLockingAnyone() {
        TestAccount victim = registered();
        Browser sprayer = newVisitor();
        for (int i = 0; i < 3; i++) {
            assertProblem(sprayer.login(newUsername(), "guess"), 401, "invalid credentials");
        }

        assertThrottled(sprayer.login(victim));

        assertThat(newVisitor().login(victim)).hasStatusOk();
    }

    @Test
    void successfulLoginsDoNotCountTowardsTheAddressThrottle() {
        TestAccount first = registered();
        TestAccount second = registered();
        Browser office = newVisitor();
        for (TestAccount account : new TestAccount[] {first, second, first, second}) {
            assertThat(office.login(account)).hasStatusOk();
            office.fetchCsrf();
        }

        for (int i = 0; i < 3; i++) {
            assertProblem(office.login(newUsername(), "guess"), 401, "invalid credentials");
        }
        assertThrottled(office.login(newUsername(), "guess"));
    }

    @Test
    void loginsPerUsernameAreThrottledIdenticallyForRealAndUnknownUsernames() {
        MvcTestResult real = thirdLoginAs(registered().username());
        MvcTestResult unknown = thirdLoginAs(newUsername());

        assertThrottled(real);
        assertThrottled(unknown);
        assertThat(real.getResponse().getContentAsByteArray()).isEqualTo(unknown.getResponse().getContentAsByteArray());
        assertThat(real.getResponse().getHeader("Retry-After")).isEqualTo(unknown.getResponse().getHeader("Retry-After"));
    }

    @Test
    void usernameThrottleFollowsTheUsernameAcrossAddressesAndCase() {
        String username = newUsername();
        newVisitor().login(username, "wrong-password");
        newVisitor().login(username.toUpperCase(), "wrong-password");

        assertThrottled(newVisitor().login(username, "wrong-password"));
    }

    @Test
    void throttledClientMayTryAgainOnceRetryAfterHasPassed() {
        String username = newUsername();
        MvcTestResult throttled = thirdLoginAs(username);
        assertThrottled(throttled);

        clock.setInstant(clock.instant().plusSeconds(Long.parseLong(throttled.getResponse().getHeader("Retry-After"))));

        assertProblem(newVisitor().login(username, "wrong-password"), 401, "invalid credentials");
    }

    @Test
    void registrationIsThrottledPerAddress() {
        Browser prober = newVisitor();
        prober.register(newAccount()).assertThat().hasStatus(201);
        prober.register(newAccount()).assertThat().hasStatus(201);

        TestAccount third = newAccount();
        assertThrottled(prober.register(third));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE username_key = ?").param(third.username())
                .query(Integer.class).single()).isZero();
        newVisitor().register(third).assertThat().hasStatus(201);
    }

    @Test
    void passwordResetRequestsAreThrottledPerAddress() {
        Browser flooder = newVisitor();
        for (int i = 0; i < 2; i++) {
            flooder.requestPasswordReset(newAccount().email()).assertThat().hasStatus(202);
        }

        assertThrottled(flooder.requestPasswordReset(newAccount().email()));
        assertThat(newVisitor().requestPasswordReset(newAccount().email())).hasStatus(202);
    }

    @Test
    void passwordResetRequestsAreThrottledPerEmailIdenticallyForRegisteredAndUnknownEmails() {
        String registered = registered().email();
        String unknown = newAccount().email();
        for (String email : new String[] {registered, unknown}) {
            newVisitor().requestPasswordReset(email).assertThat().hasStatus(202);
            newVisitor().requestPasswordReset(email.toUpperCase()).assertThat().hasStatus(202);
        }

        MvcTestResult real = newVisitor().requestPasswordReset(registered);
        MvcTestResult madeUp = newVisitor().requestPasswordReset(unknown);

        assertThrottled(real);
        assertThrottled(madeUp);
        assertThat(real.getResponse().getContentAsByteArray()).isEqualTo(madeUp.getResponse().getContentAsByteArray());
    }

    @Test
    void passwordResetConfirmsAreThrottledPerAddress() {
        Browser guesser = newVisitor();
        for (int i = 0; i < 2; i++) {
            assertProblem(guesser.confirmPasswordReset("guessed-token-" + i, "a-brand-new-password-1"),
                    400, "password reset token expired or invalid");
        }

        assertThrottled(guesser.confirmPasswordReset("guessed-token-2", "a-brand-new-password-1"));
    }

    @Test
    void forgedForwardedForIsIgnoredFromAnUntrustedPeer() {
        Browser browser = newVisitor();
        for (int i = 0; i < 3; i++) {
            assertProblem(loginForwardedFor(browser, "198.51.100." + i, newUsername()), 401, "invalid credentials");
        }

        assertThrottled(loginForwardedFor(browser, "198.51.100.99", newUsername()));
    }

    @Test
    void trustedProxysForwardedForNamesTheClient() {
        Browser proxy = newBrowserAt(TRUSTED_PROXY);
        proxy.fetchCsrf();
        String client = newAddress();
        for (int i = 0; i < 3; i++) {
            // Whatever the client claims goes first; the proxy appends the address it saw.
            loginForwardedFor(proxy, "6.6.6." + i + ", " + client, newUsername()).assertThat().hasStatus(401);
        }

        assertThrottled(loginForwardedFor(proxy, "6.6.6.99, " + client, newUsername()));
        assertProblem(loginForwardedFor(proxy, newAddress(), newUsername()), 401, "invalid credentials");
    }

    @Test
    void throttledRequestIsAuditedAtWarnWithTheHashedAddressOnly() {
        String username = newUsername();
        assertThrottled(thirdLoginAs(username));

        ILoggingEvent event = audit.single("access-control");
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(event))
                .containsEntry("event.outcome", "failure")
                .containsEntry("error_code", "429")
                .containsEntry("url.path", basePath + "/login")
                .containsKey("source.ip_hash")
                .doesNotContainKey("user.id");
        assertThat(everything(event)).doesNotContain(username, "203.0.113.");
    }
}
