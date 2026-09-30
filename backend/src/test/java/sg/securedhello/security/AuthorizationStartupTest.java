package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
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
 * The authorization configuration binds once, at refresh ({@code restart}, own database): a changed matrix takes effect
 * on the next boot and only then (ADR-043), and a matrix that leaves either admin rule set empty never starts
 * (ADR-026).
 */
class AuthorizationStartupTest {

    /** Replaces USER's matrix rows with the self-read alone, so {@code GET /api/hello} loses its row. */
    private static final String[] USER_WITHOUT_HELLO = {
        "--app.security.authorization.roles.USER[0].method=GET",
        "--app.security.authorization.roles.USER[0].path=/api/profile"};

    @TempDir
    Path database;

    private final MutableClock clock = MutableClock.startingNow();

    private Boot boot(Consumer<ConfigurableApplicationContext> whileRunning, String... args) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database),
                RestartHarness.withClock(clock)), whileRunning, args);
    }

    @Test
    @Proves("T-ADM-006")
    void aChangedMatrixDecidesTheSameRequestDifferentlyOnTheNextBootOnly() {
        AtomicReference<Account> user = new AtomicReference<>();
        AtomicReference<PortSession> session = new AtomicReference<>();
        Boot run1 = boot(context -> {
            user.set(new Accounts(context.getBean(JdbcTemplate.class), context.getBean(PasswordEncoder.class))
                    .user());
            session.set(PortSession.bootstrap(PortSession.client(context)).signIn(user.get().username(),
                    user.get().password()));
            assertThat(session.get().get("/api/hello").getStatus().value()).as("matrix A").isEqualTo(200);
        });
        assertThat(run1.failure()).isNull();

        Boot run2 = boot(context -> {
            EntityExchangeResult<String> hello = session.get().on(PortSession.client(context)).get("/api/hello");
            ProblemAssertions.assertProblem(hello.getStatus().value(),
                    hello.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), hello.getResponseBody(),
                    hello.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.ACCESS_DENIED);
            assertThat(session.get().on(PortSession.client(context)).get("/api/profile").getStatus().value())
                    .as("the row the changed matrix kept").isEqualTo(200);
        }, USER_WITHOUT_HELLO);
        assertThat(run2.failure()).isNull();
    }

    @Test
    @Proves("T-CFG-018")
    void aMatrixWithNoAdminMutationRowNeverStarts() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"),
                "--app.security.authorization.roles.ADMIN[0].method=GET",
                "--app.security.authorization.roles.ADMIN[0].path=/api/admin/users");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("admin factor rules", "reads=true, mutations=false");
    }

    @Test
    @Proves("T-CFG-018")
    void aMatrixWithNoAdminReadRowNeverStarts() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"),
                "--app.security.authorization.roles.ADMIN[0].method=DELETE",
                "--app.security.authorization.roles.ADMIN[0].path=/api/admin/users/{id}");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("admin factor rules", "reads=false, mutations=true");
    }
}
