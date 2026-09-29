package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code ctx-lockhold}: {@code ctx-default} with a 10 s H2 lock timeout, for the tests that hold a row lock until
 * another transaction is seen waiting on it, then release it: the two-admin race and the tier-2 trip's lock order
 * (ADR-048; T-MFA-007). Under {@link CtxLockTimeoutTest}'s 50 ms the other side could time out before the release,
 * so the proof would depend on scheduling. The property makes it a context of its own (ADR-065), with a database its
 * subclasses share and no other context touches, which also lets them count every enrolled admin.
 */
@TestPropertySource(properties = TemporaryH2FileInitializer.LOCK_TIMEOUT_PROPERTY + "=10000")
public abstract class CtxLockHoldTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate lockProbe;

    /**
     * Returns once some H2 session is waiting on another's row lock; fails if {@code waiter} finishes first, since it
     * then never waited. Bounded by the lock timeout, after which {@code waiter} fails and is done.
     */
    protected void awaitBlocked(Future<?> waiter) {
        while (blockedSessions() == 0) {
            if (waiter.isDone()) {
                fail("the contending transaction finished without waiting on the held lock");
            }
            Thread.onSpinWait();
        }
    }

    private int blockedSessions() {
        Integer blocked = lockProbe.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Integer.class);
        return blocked == null ? 0 : blocked;
    }
}
