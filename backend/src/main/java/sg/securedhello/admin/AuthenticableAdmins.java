package sg.securedhello.admin;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * <em>Authenticable</em>, defined once (ADR-048): an admin who can complete sign-in with both factors now. Enabled,
 * activated, password not disabled, TOTP factor enrolled (a {@code totp_user_details} row exists; ADR-053) and not
 * under tier-2 disable. Neither lock is a term: a locked-out admin is still authenticable.
 *
 * <p>It is also the {@value #GAUGE} gauge, which the deployer alerts on below two (R-OBS-007). The gauge re-evaluates
 * the predicate on every read, so it sees every channel that changes the count, including an {@code ON DELETE CASCADE}
 * of a factor row. It holds this bean strongly, so it never reads {@code NaN} after a collection (T-OBS-007).
 */
@Component
public class AuthenticableAdmins implements MeterBinder {

    /** The gauge's name. */
    public static final String GAUGE = "admins.authenticable";

    /** The five terms, over {@code users} and {@code totp_user_details}. */
    private static final String COUNT = """
            SELECT COUNT(*) FROM users u JOIN totp_user_details t ON t.user_id = u.id
            WHERE u.role = 'ADMIN' AND u.enabled AND u.activated_at IS NOT NULL
              AND u.password_disabled_at IS NULL AND t.factor_disabled_at IS NULL""";

    private final JdbcTemplate jdbc;

    public AuthenticableAdmins(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** How many admins are authenticable now. */
    public long count() {
        Long count = jdbc.queryForObject(COUNT, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(GAUGE, this, AuthenticableAdmins::count)
                .description("Admins who can sign in with both factors now (ADR-048); alert below 2")
                .strongReference(true)
                .register(registry);
    }
}
