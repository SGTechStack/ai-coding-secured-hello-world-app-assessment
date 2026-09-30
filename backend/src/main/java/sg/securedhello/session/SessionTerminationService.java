package sg.securedhello.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;

/**
 * The one seam that ends an account's sessions (ADR-037). Every trigger calls it: credential change and reset,
 * admin disable, role change, deletion, lockout and the failure cap.
 *
 * <p><b>After commit, never inside the transaction</b> (ADR-039). Called inside a transaction, the kill is registered
 * to run once it commits, outside every row lock, and a rollback cancels it. Spring Session runs its own writes in
 * {@code REQUIRES_NEW}, so there is nothing to gain by running it earlier. Called outside a transaction, it runs at
 * once. A kill that fails after commit is logged and never undoes the committed change; the startup reconciliation
 * sweep is the repair for triggers that leave durable state.
 *
 * <p>Sessions are found through the {@code PRINCIPAL_NAME} index, which holds the username the session signed in
 * with (T-SES-009).
 *
 * <p><b>The reconciliation sweep</b> ({@link #reconcile}) is that repair. It ends every session whose principal's
 * durable state says it should hold none ({@link ReconciliationTrigger}), and it is idempotent: a second run finds
 * nothing. It runs once at startup, before the port opens ({@code ReconciliationAtStartup}).
 */
@Service
public class SessionTerminationService {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminationService.class);

    /**
     * The principals of stored sessions whose account is missing or may be in a trigger state, with the columns
     * {@link ReconciliationTrigger} reads. Anonymous sessions have no principal and are never selected. The join is
     * on the {@code PRINCIPAL_NAME} index and the unique username. Any lock is selected: whether it is still in force
     * is decided on the injected clock, by {@link ReconciliationTrigger}, as sign-in decides it.
     */
    private static final String AFFECTED_PRINCIPALS = """
            SELECT DISTINCT s.PRINCIPAL_NAME, u.id, u.enabled, u.password_disabled_at, u.locked_until,
                   t.factor_disabled_at
            FROM SPRING_SESSION s
            LEFT JOIN users u ON u.username = s.PRINCIPAL_NAME
            LEFT JOIN totp_user_details t ON t.user_id = u.id
            WHERE s.PRINCIPAL_NAME IS NOT NULL
              AND (u.id IS NULL OR u.enabled = FALSE OR u.password_disabled_at IS NOT NULL OR u.locked_until IS NOT NULL
                   OR t.factor_disabled_at IS NOT NULL)""";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final JdbcTemplate jdbc;
    private final AuditEmitter audit;
    private final Clock clock;

    public SessionTerminationService(FindByIndexNameSessionRepository<? extends Session> sessions, JdbcTemplate jdbc,
            AuditEmitter audit, Clock clock) {
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ends every session of {@code username}: reset redemption and the admin and lockout triggers. */
    public void endAll(String username) {
        dispatch(username, null);
    }

    /**
     * Ends every session of {@code username} except {@code keptSessionId}: self-service change and forced-change
     * completion, where the acting session survives (ADR-035). The caller rotates the kept session's id afterwards.
     */
    public void endAllExcept(String username, String keptSessionId) {
        dispatch(username, keptSessionId);
    }

    private void dispatch(String username, @Nullable String keptSessionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            end(username, keptSessionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                end(username, keptSessionId);
            }
        });
    }

    private void end(String username, @Nullable String keptSessionId) {
        try {
            delete(username, keptSessionId);
        } catch (RuntimeException ex) {
            log.error("Ending an account's sessions after commit failed; the committed change stands", ex);
        }
    }

    /**
     * The reconciliation sweep (ADR-039; R-SES-012): ends every session of every account whose durable state says it
     * should hold none, and writes one audit row with the counts, zeros included. Unaffected sessions, and anonymous
     * ones, are left alone. Unlike an after-commit dispatch, a failure propagates: at startup it stops the application
     * before traffic is served, rather than serving a session that should have ended.
     *
     * @return what the sweep ended, as the audit row records it
     */
    public Reconciliation reconcile() {
        Instant now = clock.instant();
        Map<ReconciliationTrigger, Integer> accounts = new EnumMap<>(ReconciliationTrigger.class);
        int ended = 0;
        for (Affected affected : jdbc.query(AFFECTED_PRINCIPALS, SessionTerminationService::affected)) {
            Optional<ReconciliationTrigger> trigger = ReconciliationTrigger.of(affected.standing(), now);
            if (trigger.isPresent()) {
                accounts.merge(trigger.get(), 1, Integer::sum);
                ended += delete(affected.principal(), null);
            }
        }
        Reconciliation reconciliation = new Reconciliation(ended, accounts);
        audit.emit(AuditEvent.SESSIONS_RECONCILED, reconciliation.auditContext());
        return reconciliation;
    }

    /** A selected principal and its account's standing, read with typed getters so no trigger fails open. */
    private record Affected(String principal, ReconciliationTrigger.Standing standing) {
    }

    private static Affected affected(ResultSet rs, int rowNumber) throws SQLException {
        OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
        return new Affected(rs.getString("principal_name"), new ReconciliationTrigger.Standing(
                rs.getObject("id", UUID.class) != null, rs.getBoolean("enabled"),
                rs.getObject("password_disabled_at", OffsetDateTime.class) != null,
                lockedUntil == null ? null : lockedUntil.toInstant(),
                rs.getObject("factor_disabled_at", OffsetDateTime.class) != null));
    }

    private int delete(String username, @Nullable String keptSessionId) {
        List<String> ids = sessions.findByPrincipalName(username).keySet().stream()
                .filter(id -> !id.equals(keptSessionId))
                .toList();
        ids.forEach(sessions::deleteById);
        return ids.size();
    }
}
