package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

/** The one session-ending seam dispatches after commit, outside the transaction, and never on rollback (ADR-039). */
class SessionTerminationServiceTest extends CtxDefaultTest {

    @Autowired
    private SessionTerminationService sessions;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SessionRows rows;
    private Account account;
    private String sessionId;

    @BeforeEach
    void signIn() throws Exception {
        rows = new SessionRows(jdbc);
        account = new Accounts(jdbc, passwordEncoder).user();
        sessionId = SessionRows.idOf(SignedIn.as(mockMvc, account).cookie().getValue());
    }

    @Test
    void insideATransactionTheSessionsEndOnlyOnceItCommits() {
        transactions.executeWithoutResult(status -> {
            sessions.endAll(account.username());
            assertThat(rows.exists(sessionId)).as("still there before commit").isTrue();
        });
        assertThat(rows.exists(sessionId)).isFalse();
    }

    @Test
    void aRollbackEndsNothing() {
        assertThatIllegalStateException().isThrownBy(() -> transactions.executeWithoutResult(status -> {
            sessions.endAll(account.username());
            throw new IllegalStateException("the state change failed");
        }));
        assertThat(rows.exists(sessionId)).isTrue();
    }

    @Test
    void outsideATransactionTheSessionsEndAtOnce() {
        sessions.endAll(account.username());
        assertThat(rows.exists(sessionId)).isFalse();
    }

    @Test
    void theKeptSessionSurvives() {
        sessions.endAllExcept(account.username(), sessionId);
        assertThat(rows.exists(sessionId)).isTrue();
    }
}
