package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code F_base}, measured rather than argued (R-RL-009; R-OBS-012): the H2 file at its normal size for a population of
 * P = 100 signed-in accounts, each with its password history and a live session, stays under {@link #F_BASE_BYTES}
 * while the database is open and after it closes. The sizing line in R-RL-009 adds this bound to the volume.
 */
class DatabaseBaseSizeTest {

    /**
     * The pinned upper bound of the H2 file at P = 100: 8 MiB. Measured on H2 2.4 at 389,120 bytes open after a
     * checkpoint and 487,424 bytes closed; the bound leaves room for the session churn and the free pages a long-running
     * file keeps (ADR-041). R-RL-009 records it.
     */
    static final long F_BASE_BYTES = 8L * 1024 * 1024;

    private static final int P = 100;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Every budget raised, so one loopback source can sign in P accounts without waiting for a refill. */
    private static final String[] RAISED_BUDGETS = {"--app.security.rate-limit.csrf.source.burst=1000000",
            "--app.security.rate-limit.login.source.burst=1000000",
            "--app.security.rate-limit.login.username.burst=1000000"};

    @TempDir
    Path directory;

    private static void signIn(RestTestClient client, Account account) {
        EntityExchangeResult<String> bootstrap = client.get().uri("/api/csrf").exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult();
        String cookie = bootstrap.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        client.post().uri("/api/login").header(HttpHeaders.COOKIE, cookie)
                .header("X-CSRF-TOKEN", JSON.readTree(bootstrap.getResponseBody()).get("token").asString())
                .contentType(MediaType.APPLICATION_JSON).body(SignedIn.credentials(account.username(), account.password()))
                .exchange().expectStatus().isOk();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @Proves("T-OBS-017")
    void theDatabaseFileAtAHundredUsersIsUnderThePinnedBaseSize() {
        AtomicReference<Path> file = new AtomicReference<>();
        AtomicLong open = new AtomicLong();

        Boot boot = RestartHarness.run(builder -> builder.profiles("dev")
                .initializers(RestartHarness.onDatabase(directory)), context -> {
                    populate(context);
                    context.getBean(JdbcTemplate.class).execute("CHECKPOINT SYNC");
                    file.set(context.getBean(DatabaseFile.class).file());
                    open.set(size(file.get()));
                }, RAISED_BUDGETS);
        assertThat(boot.failure()).isNull();
        long closed = size(file.get());

        assertThat(open.get()).as("the open file at P = %d", P).isLessThanOrEqualTo(F_BASE_BYTES);
        assertThat(closed).as("the closed file at P = %d", P).isLessThanOrEqualTo(F_BASE_BYTES);
    }

    private static void populate(ConfigurableApplicationContext context) {
        Accounts accounts = new Accounts(context.getBean(JdbcTemplate.class), context.getBean(PasswordEncoder.class));
        RestTestClient client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + context.getEnvironment().getProperty("local.server.port")).build();
        for (int i = 0; i < P; i++) {
            signIn(client, accounts.user());
        }
        assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL", Long.class))
                .as("a live session per account").isGreaterThanOrEqualTo(P);
    }
}
