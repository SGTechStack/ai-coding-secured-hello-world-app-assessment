package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * A password lock is stored state, not memory: it survives a full close and a second boot on the same H2 file
 * ({@code restart}, own database; T-LCK-007). Both boots share one forward-only clock, so the lock is outlived by
 * advancing it, never by sleeping.
 */
class LockoutRestartTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path database;

    private final MutableClock clock = MutableClock.startingNow();

    private Boot boot(Consumer<ConfigurableApplicationContext> whileRunning) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database),
                RestartHarness.withClock(clock)), whileRunning);
    }

    /** Posts a login on a freshly bootstrapped anonymous session over the real port, as the SPA does. */
    private static EntityExchangeResult<String> login(ConfigurableApplicationContext context, String username,
            String password) {
        RestTestClient client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + context.getEnvironment().getProperty("local.server.port")).build();
        EntityExchangeResult<String> bootstrap = client.get().uri("/api/csrf").exchange()
                .expectBody(String.class).returnResult();
        String cookie = bootstrap.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        return client.post().uri("/api/login").header(HttpHeaders.COOKIE, cookie)
                .header("X-CSRF-TOKEN", JSON.readTree(bootstrap.getResponseBody()).get("token").asString())
                .contentType(MediaType.APPLICATION_JSON).body(SignedIn.credentials(username, password))
                .exchange().expectBody(String.class).returnResult();
    }

    private static void assertUniformRefusal(EntityExchangeResult<String> result) {
        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), result.getResponseBody(),
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    @Proves("T-LCK-007")
    void aLockTakenBeforeARestartStillRefusesTheCorrectPasswordUntilItLapses() {
        AtomicReference<Account> locked = new AtomicReference<>();
        AtomicReference<Instant> lockedUntil = new AtomicReference<>();

        Boot first = boot(context -> {
            Accounts accounts = new Accounts(context.getBean(JdbcTemplate.class), context.getBean(PasswordEncoder.class));
            Account account = accounts.user();
            for (int i = 0; i < context.getBean(LockoutProperties.class).threshold(); i++) {
                assertUniformRefusal(login(context, account.username(), Accounts.WRONG_PASSWORD));
            }
            lockedUntil.set(accounts.lockoutState(account).lockedUntil());
            assertThat(lockedUntil.get()).as("locked in the first boot").isAfter(clock.instant());
            locked.set(account);
        });
        assertThat(first.failure()).isNull();

        Boot second = boot(context -> {
            Account account = locked.get();
            assertUniformRefusal(login(context, account.username(), account.password()));

            clock.advance(Duration.between(clock.instant(), lockedUntil.get()).minusSeconds(1));
            assertUniformRefusal(login(context, account.username(), account.password()));

            clock.advance(Duration.ofSeconds(1));
            assertThat(login(context, account.username(), account.password()).getStatus().value())
                    .as("admitted once the shared clock reaches locked_until").isEqualTo(200);
        });
        assertThat(second.failure()).isNull();
    }
}
