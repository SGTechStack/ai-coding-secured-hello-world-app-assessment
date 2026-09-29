package sg.securedhello.admin;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <em>Authenticable</em>, defined once (ADR-048), in {@link Standing}: an admin who can complete sign-in with both
 * factors now. Enabled, activated, password not disabled, TOTP factor enrolled (a {@code totp_user_details} row
 * exists; ADR-053) and not under tier-2 disable. Neither lock is a term: a locked-out admin is still authenticable.
 *
 * <p>It has two readers, and both evaluate the same {@link Standing}:
 * <ul>
 *   <li>the {@value #GAUGE} gauge, which counts {@link Standing#authenticable()} and which the deployer alerts on below
 *       two (R-OBS-007). It re-evaluates on every read, so it sees every channel that changes the count, including an
 *       {@code ON DELETE CASCADE} of a factor row. It holds this bean strongly, so it never reads {@code NaN} after a
 *       collection (T-OBS-007);</li>
 *   <li>{@code AdminActionGuard}, which counts {@link Standing#enrolledAdmin()}, the enrolment terms only, over the
 *       rows {@link #lockForChange} locked.</li>
 * </ul>
 */
@Component
public class AuthenticableAdmins implements MeterBinder {

    /** The gauge's name. */
    public static final String GAUGE = "admins.authenticable";

    /** Every admin, with its factor row if it has one; no lock. */
    private static final String ADMINS = """
            SELECT u.id, u.role, u.enabled, u.activated_at, u.password_disabled_at,
                   t.user_id AS factor_user_id, t.factor_disabled_at
            FROM users u LEFT JOIN totp_user_details t ON t.user_id = u.id
            WHERE u.role = 'ADMIN'""";

    /**
     * The first half of the guard's lock set: every admin row and the subject's. H2 allows no aggregate under
     * {@code FOR UPDATE}, so the rows are read and counted in Java (ADR-048).
     */
    private static final String LOCK_USERS = """
            SELECT id, role, enabled, activated_at, password_disabled_at FROM users
            WHERE role = 'ADMIN' OR id = ? ORDER BY id FOR UPDATE""";

    /** The second half: those rows' factor rows, taken after the {@code users} rows (lock order, ADR-048). */
    private static final String LOCK_FACTORS = """
            SELECT user_id, factor_disabled_at FROM totp_user_details
            WHERE user_id IN (SELECT id FROM users WHERE role = 'ADMIN' OR id = ?) ORDER BY user_id FOR UPDATE""";

    private final JdbcTemplate jdbc;

    public AuthenticableAdmins(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One account's standing: the columns every term of the predicate reads, over {@code users} and
     * {@code totp_user_details}.
     *
     * @param id               the account
     * @param admin            its role is {@code ADMIN}
     * @param enabled          an administrator has left it enabled
     * @param activated        {@code activated_at} is set, so a pending invite is not (ADR-006)
     * @param passwordDisabled the NIST failure cap disabled its password (ADR-013)
     * @param enrolled         a {@code totp_user_details} row exists (ADR-053)
     * @param factorDisabled   its factor is under tier-2 disable (ADR-027)
     */
    public record Standing(UUID id, boolean admin, boolean enabled, boolean activated, boolean passwordDisabled,
            boolean enrolled, boolean factorDisabled) {

        /** The enrolment terms, which the two-admin invariant counts: an enabled, activated, enrolled admin. */
        public boolean enrolledAdmin() {
            return admin && enabled && activated && enrolled;
        }

        /** All five terms: an enrolled admin whose password and factor are both usable now. */
        public boolean authenticable() {
            return enrolledAdmin() && !passwordDisabled && !factorDisabled;
        }
    }

    /** How many admins are authenticable now. */
    public long count() {
        return jdbc.query(ADMINS, (rs, row) -> UserColumns.of(rs).standing(rs.getObject("factor_user_id") != null,
                        rs.getObject("factor_disabled_at") != null)).stream()
                .filter(Standing::authenticable)
                .count();
    }

    /**
     * Locks the guard's uniform lock set in the caller's transaction and returns its standings: every admin row and
     * {@code subject}'s, {@code FOR UPDATE}, then their factor rows (ADR-048). Every guarded path takes the same set,
     * whichever tables its own statement names. Empty of {@code subject} when no account has that id.
     *
     * <p>It holds every admin's {@code users} row until the caller commits, so a guarded mutation briefly serialises
     * against admin sign-in and factor verification, which lock their own row. The transaction is a few statements long;
     * the uniform set is what ADR-048 requires, and the order is the one every path takes, so it is a wait, never a
     * deadlock (T-MFA-007).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Standing> lockForChange(UUID subject) {
        List<UserColumns> users = jdbc.query(LOCK_USERS, (rs, row) -> UserColumns.of(rs), subject);
        // Each locked factor row: its user, and whether it is under tier-2 disable.
        Map<UUID, Boolean> factors = new HashMap<>();
        jdbc.query(LOCK_FACTORS, (RowCallbackHandler) rs -> factors.put(rs.getObject("user_id", UUID.class),
                rs.getObject("factor_disabled_at") != null), subject);
        return users.stream()
                .map(user -> user.standing(factors.containsKey(user.id()), factors.getOrDefault(user.id(), false)))
                .toList();
    }

    /** The {@code users} half of a {@link Standing}, as both queries read it. */
    private record UserColumns(UUID id, boolean admin, boolean enabled, boolean activated, boolean passwordDisabled) {

        static UserColumns of(ResultSet rs) throws SQLException {
            return new UserColumns(rs.getObject("id", UUID.class), "ADMIN".equals(rs.getString("role")),
                    rs.getBoolean("enabled"), rs.getObject("activated_at") != null,
                    rs.getObject("password_disabled_at") != null);
        }

        Standing standing(boolean enrolled, boolean factorDisabled) {
            return new Standing(id, admin, enabled, activated, passwordDisabled, enrolled, factorDisabled);
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(GAUGE, this, AuthenticableAdmins::count)
                .description("Admins who can sign in with both factors now (ADR-048); alert below 2")
                .strongReference(true)
                .register(registry);
    }
}
