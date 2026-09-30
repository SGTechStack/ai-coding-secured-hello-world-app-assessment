package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The reconciliation sweep (ADR-039; R-SES-012): with a trigger's state change committed and its session kill never
 * dispatched, the sweep ends the subject's sessions, leaves everyone else's alone, and a second run changes nothing.
 * The state changes are written straight to the database, which is exactly a commit whose dispatch was lost.
 */
class SessionReconciliationTest extends CtxDefaultTest {

    private static final String ROW = "Sessions reconciled at startup.";

    @Autowired
    private SessionTerminationService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private Clock clock;

    private Accounts accounts;
    private SessionRows rows;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        rows = new SessionRows(jdbc);
        // Start from a store the sweep has nothing to do in, whatever earlier tests in this context left behind.
        sessions.reconcile();
    }

    private String signIn(Account account) throws Exception {
        return SessionRows.idOf(SignedIn.as(mockMvc, account).cookie().getValue());
    }

    /** T-SES-022: the NIST cap set {@code password_disabled_at}, and the kill was never dispatched. */
    @Test
    @Proves("T-SES-022")
    void theSweepEndsACappedAccountsSessionAndASecondRunChangesNothing() throws Exception {
        Account capped = accounts.user();
        Account bystander = accounts.user();
        String cappedSession = signIn(capped);
        String bystanderSession = signIn(bystander);
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                capped.id());

        try (AuditCapture audit = AuditCapture.start()) {
            Reconciliation first = sessions.reconcile();

            assertThat(rows.exists(cappedSession)).isFalse();
            assertThat(rows.exists(bystanderSession)).isTrue();
            assertThat(first.sessionsEnded()).isEqualTo(1);
            assertThat(first.accounts()).containsExactly(Map.entry(ReconciliationTrigger.CAPPED, 1));

            Reconciliation second = sessions.reconcile();

            assertThat(second.sessionsEnded()).isZero();
            assertThat(second.accounts()).isEmpty();
            assertThat(rows.exists(bystanderSession)).isTrue();

            List<Map<String, Object>> written = audit.withMessage(ROW);
            assertThat(written).hasSize(2);
            assertThat(written.get(0)).containsEntry("event.action", "session-reconciliation")
                    .containsEntry("session.ended_count", 1)
                    .containsEntry("labels.reconciled_accounts", List.of("DELETED=0", "DISABLED=0", "CAPPED=1",
                            "LOCKED=0", "FACTOR_DISABLED=0"))
                    .doesNotContainKey("user.id");
            assertThat(written.get(1)).containsEntry("session.ended_count", 0);
        }
    }

    static Stream<Arguments> triggers() {
        return Stream.of(
                Arguments.of("admin disable", ReconciliationTrigger.DISABLED,
                        (BiConsumer<SessionReconciliationTest, Account>) (test, account) -> test.jdbc.update(
                                "UPDATE users SET enabled = FALSE WHERE id = ?", account.id())),
                Arguments.of("deletion (soft delete)", ReconciliationTrigger.DELETED,
                        (BiConsumer<SessionReconciliationTest, Account>) (test, account) -> test.jdbc.update(
                                "DELETE FROM users WHERE id = ?", account.id())),
                Arguments.of("in-force password lock", ReconciliationTrigger.LOCKED,
                        (BiConsumer<SessionReconciliationTest, Account>) (test, account) -> test.jdbc.update(
                                "UPDATE users SET locked_until = ? WHERE id = ?",
                                Timestamp.from(test.clock.instant().plus(Duration.ofMinutes(20))), account.id())),
                Arguments.of("tier-2 factor disable", ReconciliationTrigger.FACTOR_DISABLED,
                        (BiConsumer<SessionReconciliationTest, Account>) (test, account) -> {
                            new TotpFactors(test.jdbc, test.cipher, test.clock).enrol(account);
                            test.jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                                    Timestamp.from(test.clock.instant()), account.id());
                        }));
    }

    /** T-SES-036: every other durable-state trigger, each committed with its kill never dispatched. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("triggers")
    @Proves("T-SES-036")
    void theSweepEndsEverySessionOfTheSubjectAndASecondRunChangesNothing(String trigger, ReconciliationTrigger counted,
            BiConsumer<SessionReconciliationTest, Account> commit) throws Exception {
        Account subject = accounts.user();
        Account bystander = accounts.user();
        String subjectSession = signIn(subject);
        String bystanderSession = signIn(bystander);
        commit.accept(this, subject);

        Reconciliation first = sessions.reconcile();

        assertThat(rows.exists(subjectSession)).isFalse();
        assertThat(rows.exists(bystanderSession)).isTrue();
        assertThat(first.accounts()).containsExactly(Map.entry(counted, 1));
        assertThat(first.sessionsEnded()).isEqualTo(1);

        Reconciliation second = sessions.reconcile();

        assertThat(second.sessionsEnded()).isZero();
        assertThat(second.accounts()).isEmpty();
        assertThat(rows.exists(bystanderSession)).isTrue();
    }

    /** A lock that has run out, a tier-1 factor lock and an anonymous session are no triggers. */
    @Test
    void theSweepLeavesSessionsItHasNoDurableReasonToEnd() throws Exception {
        Account lapsed = accounts.user();
        Account factorLocked = accounts.user();
        String lapsedSession = signIn(lapsed);
        String factorLockedSession = signIn(factorLocked);
        new TotpFactors(jdbc, cipher, clock).enrol(factorLocked);
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?", Timestamp.from(clock.instant().minusSeconds(1)),
                lapsed.id());
        jdbc.update("UPDATE totp_user_details SET locked_until = ? WHERE user_id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), factorLocked.id());
        String anonymous = SessionRows.idOf(CsrfSession.bootstrap(mockMvc).cookie()
                .getValue());

        assertThat(sessions.reconcile().sessionsEnded()).isZero();
        assertThat(List.of(lapsedSession, factorLockedSession, anonymous)).allMatch(rows::exists);
    }
}
