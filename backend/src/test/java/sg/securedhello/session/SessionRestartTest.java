package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.PortSession;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * Sessions and their CSRF tokens are stored state, not memory: both survive a full close and a second boot on the same
 * H2 file ({@code restart}, own database). The boots share one forward-only clock.
 */
class SessionRestartTest {

    @TempDir
    Path database;

    private final MutableClock clock = MutableClock.startingNow();

    private Boot boot(Consumer<ConfigurableApplicationContext> whileRunning) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database),
                RestartHarness.withClock(clock)), whileRunning);
    }

    private static Account account(ConfigurableApplicationContext context) {
        return new Accounts(context.getBean(JdbcTemplate.class), context.getBean(PasswordEncoder.class)).user();
    }

    private static void assertProblem(EntityExchangeResult<String> result, ErrorCode code) {
        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), result.getResponseBody(),
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), code);
    }

    @Test
    @Proves("T-SES-008")
    void aSignInAfterARestartStillDisplacesTheSessionFromBeforeIt() {
        AtomicReference<Account> user = new AtomicReference<>();
        AtomicReference<PortSession> first = new AtomicReference<>();
        Boot run1 = boot(context -> {
            user.set(account(context));
            first.set(PortSession.bootstrap(PortSession.client(context)).signIn(user.get().username(),
                    user.get().password()));
            assertThat(first.get().get("/api/profile").getStatus().value()).isEqualTo(200);
        });
        assertThat(run1.failure()).isNull();

        Boot run2 = boot(context -> {
            PortSession second = PortSession.bootstrap(PortSession.client(context)).signIn(user.get().username(),
                    user.get().password());
            PortSession sessionA = first.get().on(PortSession.client(context));

            assertProblem(sessionA.get("/api/profile"), ErrorCode.AUTHENTICATION_FAILED);
            assertThat(second.get("/api/profile").getStatus().value()).isEqualTo(200);
        });
        assertThat(run2.failure()).isNull();
    }

    @Test
    @Proves("T-CSRF-004")
    void aTokenFetchedBeforeARestartStillAuthorisesAnUnsafeRequestAfterIt() {
        AtomicReference<PortSession> signedIn = new AtomicReference<>();
        Boot run1 = boot(context -> {
            Account user = account(context);
            signedIn.set(PortSession.bootstrap(PortSession.client(context)).signIn(user.username(), user.password()));
        });
        assertThat(run1.failure()).isNull();

        Boot run2 = boot(context -> {
            PortSession carried = signedIn.get().on(PortSession.client(context));
            // Not vacuous: the session is live and CSRF is checked, so a token that is not the run-1 one is refused.
            assertProblem(new PortSession(carried.client(), carried.cookie(), "not-the-token").send(HttpMethod.POST,
                    "/api/logout", "{}"), ErrorCode.CSRF_TOKEN_INVALID);
            assertThat(carried.send(HttpMethod.POST, "/api/logout", "{}").getStatus().value())
                    .as("sign-out with the run-1 cookie and token").isEqualTo(204);
        });
        assertThat(run2.failure()).isNull();
    }
}
