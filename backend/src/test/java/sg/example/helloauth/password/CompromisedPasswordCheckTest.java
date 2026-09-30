package sg.example.helloauth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.account.AccountService;
import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.OfflineCompromisedPasswords;
import sg.example.helloauth.support.TestAccount;

/**
 * The real Have I Been Pwned checker against a stand-in for its range API, so the wiring (base
 * URL, timeout, what an outage does) is tested without the network.
 */
class CompromisedPasswordCheckTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static final PwnedPasswordsStub pwnedPasswords = PwnedPasswordsStub.start();

    @DynamicPropertySource
    static void useTheStub(DynamicPropertyRegistry properties) {
        properties.add(OfflineCompromisedPasswords.PROPERTY, () -> "real");
        properties.add("app.password.compromised-check.base-url", pwnedPasswords::baseUrl);
        properties.add("app.password.compromised-check.timeout", () -> "500ms");
    }

    @Autowired
    private AccountService accounts;

    @AfterEach
    void healTheStub() {
        pwnedPasswords.reset();
    }

    @AfterAll
    static void stopTheStub() {
        pwnedPasswords.stop();
    }

    private MvcTestResult register(TestAccount account) {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser.register(account);
    }

    private MvcTestResult login(TestAccount account) {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser.login(account);
    }

    @Test
    void passwordThatTheApiReportsAsBreachedIsRejectedAtRegistration() {
        pwnedPasswords.reportBreached(ALICE.password());

        assertProblem(register(ALICE), 400, "validation failed")
                .hasPathSatisfying("$.errors[0].message",
                        message -> assertThat(message).isEqualTo(PasswordPolicy.COMPROMISED));
    }

    @Test
    void passwordThatTheApiDoesNotReportIsAccepted() {
        pwnedPasswords.reportBreached("some-other-password");

        assertThat(register(ALICE)).hasStatus(201);
    }

    @Test
    void registrationIsRefusedWhileTheApiIsFailing() {
        pwnedPasswords.fail();

        assertProblem(register(ALICE), 503, "service unavailable");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users").query(Integer.class).single()).isZero();
    }

    @Test
    void registrationIsRefusedWhenTheApiTimesOut() {
        pwnedPasswords.hang();

        assertProblem(register(ALICE), 503, "service unavailable");
    }

    @Test
    void correctPasswordThatTheApiReportsAsBreachedCannotLogIn() {
        accounts.register(ALICE.username(), ALICE.email(), ALICE.password());
        pwnedPasswords.reportBreached(ALICE.password());

        assertProblem(login(ALICE), 401, "invalid credentials");
    }

    @Test
    void correctPasswordStillLogsInWhileTheApiIsFailing() {
        accounts.register(ALICE.username(), ALICE.email(), ALICE.password());
        pwnedPasswords.fail();

        assertThat(login(ALICE)).hasStatusOk();
    }

    /** Answers {@code GET /range/{prefix}} as the Pwned Passwords API does, or fails on request. */
    private static final class PwnedPasswordsStub {

        private final HttpServer server;
        private volatile String breachedSha1 = "";
        private volatile int failureStatus;
        private volatile CountDownLatch hang;

        private PwnedPasswordsStub(HttpServer server) {
            this.server = server;
        }

        static PwnedPasswordsStub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                PwnedPasswordsStub stub = new PwnedPasswordsStub(server);
                server.createContext("/range/", stub::handle);
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                return stub;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/range/";
        }

        void reportBreached(String password) {
            breachedSha1 = sha1(password);
        }

        void fail() {
            failureStatus = 503;
        }

        void hang() {
            hang = new CountDownLatch(1);
        }

        void reset() {
            breachedSha1 = "";
            failureStatus = 0;
            if (hang != null) {
                hang.countDown();
                hang = null;
            }
        }

        void stop() {
            reset();
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                CountDownLatch waitFor = hang;
                if (waitFor != null) {
                    waitFor.await();
                }
                if (failureStatus != 0) {
                    exchange.sendResponseHeaders(failureStatus, -1);
                    return;
                }
                String prefix = exchange.getRequestURI().getPath().substring("/range/".length());
                // Real responses list many suffixes with counts; one that doesn't match comes first.
                String body = "0000000000000000000000000000000000A:1\r\n"
                        + (breachedSha1.startsWith(prefix) ? breachedSha1.substring(5) + ":42\r\n" : "");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private static String sha1(String password) {
            try {
                return HexFormat.of().withUpperCase().formatHex(
                        MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
