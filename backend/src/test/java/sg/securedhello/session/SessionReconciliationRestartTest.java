package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The startup sweep across real boots on one H2 file ({@code restart}, own database; ADR-039; R-SES-012). The first
 * boot leaves sessions alive behind each committed trigger, as a crash between commit and dispatch would; the second
 * boot ends them before its port opens and leaves everyone else's; a third finds nothing to do.
 */
@ExtendWith(OutputCaptureExtension.class)
class SessionReconciliationRestartTest {

    private static final String ACTION = "session-reconciliation";

    @TempDir
    Path database;

    private Boot boot(Consumer<ConfigurableApplicationContext> whileRunning,
            ApplicationListener<WebServerInitializedEvent> atPortOpen) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database))
                .listeners(atPortOpen), whileRunning);
    }

    /** A stored session signed in as {@code username}, as the login flow leaves it. */
    private static String session(ConfigurableApplicationContext context, String username) {
        return save(context.getBean(JdbcIndexedSessionRepository.class), username);
    }

    private static <S extends Session> String save(FindByIndexNameSessionRepository<S> repository, String username) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        repository.save(session);
        return session.getId();
    }

    @Test
    @Proves({"T-SES-022", "T-SES-036"})
    void theSecondBootEndsEverySessionEachTriggerLeftAliveBeforeItsPortOpens(CapturedOutput output) {
        Map<String, List<String>> doomed = new HashMap<>();
        Map<String, String> survivors = new HashMap<>();

        Boot first = boot(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Accounts accounts = new Accounts(jdbc, context.getBean(PasswordEncoder.class));
            Instant now = context.getBean(Clock.class).instant();
            Map<String, Consumer<Account>> triggers = Map.of(
                    "disabled", account -> jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", account.id()),
                    "deleted", account -> jdbc.update("DELETE FROM users WHERE id = ?", account.id()),
                    "locked", account -> jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                            Timestamp.from(now.plus(Duration.ofHours(1))), account.id()),
                    "capped", account -> jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?",
                            Timestamp.from(now), account.id()),
                    "factor-disabled", account -> {
                        new TotpFactors(jdbc, context.getBean(TotpSecretCipher.class), context.getBean(Clock.class))
                                .enrol(account);
                        jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                                Timestamp.from(now), account.id());
                    });
            triggers.forEach((trigger, commit) -> {
                Account account = accounts.user();
                // Two sessions each, left behind by a store that holds more than one: the sweep ends all of them.
                doomed.put(trigger, List.of(session(context, account.username()),
                        session(context, account.username())));
                commit.accept(account);
            });
            Account unaffected = accounts.user();
            survivors.put("unaffected", session(context, unaffected.username()));
            Account lapsedLock = accounts.user();
            survivors.put("lapsed lock", session(context, lapsedLock.username()));
            jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?", Timestamp.from(now.minusSeconds(1)),
                    lapsedLock.id());
        }, event -> { });
        assertThat(first.failure()).isNull();
        int mark = output.getOut().length();

        AtomicReference<Set<String>> atPortOpen = new AtomicReference<>();
        Boot second = boot(context -> {
            Set<String> ids = new SessionRows(context.getBean(JdbcTemplate.class)).ids();
            doomed.forEach((trigger, left) -> assertThat(ids).as(trigger).doesNotContainAnyElementsOf(left));
            assertThat(ids).as("sessions of unaffected users").containsAll(survivors.values());
        }, event -> atPortOpen.set(new SessionRows(event.getApplicationContext().getBean(JdbcTemplate.class)).ids()));

        assertThat(second.failure()).isNull();
        assertThat(atPortOpen.get()).as("the store as the port opened").containsExactlyInAnyOrderElementsOf(
                survivors.values());
        List<Map<String, Object>> swept = EcsJson.rowsWithAction(output.getOut().substring(mark), ACTION);
        assertThat(swept).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("log.logger", "audit")
                .containsEntry("event.type", List.of("end"))
                .containsEntry("session.ended_count", 10)
                .containsEntry("labels.reconciled_accounts", List.of("DELETED=1", "DISABLED=1", "CAPPED=1",
                        "LOCKED=1", "FACTOR_DISABLED=1")));
        mark = output.getOut().length();

        Boot third = boot(context -> assertThat(new SessionRows(context.getBean(JdbcTemplate.class)).ids())
                .containsExactlyInAnyOrderElementsOf(survivors.values()), event -> { });

        assertThat(third.failure()).isNull();
        assertThat(EcsJson.rowsWithAction(output.getOut().substring(mark), ACTION)).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("session.ended_count", 0));
    }

    /**
     * Fail closed: a sweep that cannot end the sessions it found stops startup before the port opens, rather than
     * serving them. Here the repository's table is renamed away on the second boot, so its delete fails.
     */
    @Test
    void aSweepThatFailsStopsStartupBeforeThePortOpens() {
        Boot first = boot(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Account account = new Accounts(jdbc, context.getBean(PasswordEncoder.class)).user();
            session(context, account.username());
            jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", account.id());
        }, event -> { });
        assertThat(first.failure()).isNull();

        Boot second = RestartHarness.run(builder -> builder.profiles("dev")
                .initializers(RestartHarness.onDatabase(database)), context -> { },
                "--spring.session.jdbc.table-name=NO_SUCH_SESSION_TABLE");

        assertThat(second.portOpened()).isFalse();
        assertThat(second.failure()).isNotNull();
        assertThat(second.failure()).rootCause().hasMessageContaining("NO_SUCH_SESSION_TABLE");
        assertThat(stackOf(second.failure())).contains(ReconciliationAtStartup.class.getName());
    }

    private static String stackOf(Throwable failure) {
        java.io.StringWriter out = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
    }
}
