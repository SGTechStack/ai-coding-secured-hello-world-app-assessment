package sg.securedhello.session.shedding;

import java.time.Clock;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.persistence.DatabaseFile;

/** Anonymous-session shedding and the {@code h2Data} health indicator that shares its state (ADR-041). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SheddingProperties.class)
public class SheddingConfig {

    /** The gauge of stored anonymous session rows. */
    public static final String ANONYMOUS_ROWS_GAUGE = "sessions.anonymous.rows";

    @Bean
    AnonymousSessionShedding anonymousSessionShedding(JdbcTemplate jdbc, SessionStoreVolume volume, Clock clock,
            AuditEmitter audit, SheddingProperties properties) {
        // Unfiltered, so H2 answers it from its quick-aggregate path (R-RL-013).
        AnonymousSessionShedding.RowCount rows = () -> {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Long.class);
            return count == null ? 0 : count;
        };
        return new AnonymousSessionShedding(rows, volume, clock, audit, properties.floor().toBytes());
    }

    /**
     * The volume of {@code app.db.data-dir}, which holds the H2 file (R-OBS-019). Looked up on each read, and a failed
     * read sheds.
     */
    @Bean
    SessionStoreVolume sessionStoreVolume(DatabaseFile databaseFile) {
        return databaseFile::usableBytes;
    }

    /**
     * Stored anonymous session rows, expired ones the cleanup job has not yet deleted included, since stored rows are
     * what fill the disk; counted at publish over the {@code PRINCIPAL_NAME} index: the one signal that tells
     * session growth from audit growth on a shared mount. Anonymous means no principal, which only password sign-in
     * sets (ADR-038). The metrics are pushed, never served, so nobody outside sets its query rate; the shed check keeps
     * its own cached count (ADR-041). The state object is strongly held, so the gauge never reads {@code NaN} after a
     * collection (T-OBS-016).
     */
    @Bean
    MeterBinder anonymousSessionRowsGauge(JdbcTemplate jdbc) {
        return registry -> Gauge.builder(ANONYMOUS_ROWS_GAUGE, jdbc, SheddingConfig::anonymousRows)
                .description("Stored anonymous session rows (ADR-041)")
                .strongReference(true)
                .register(registry);
    }

    private static double anonymousRows(JdbcTemplate jdbc) {
        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NULL", Long.class);
        return rows == null ? 0 : rows;
    }

    /**
     * {@code h2Data}: DOWN while a shed episode runs. It reads the episode the last shed check decided and never runs
     * a count of its own, because anyone can reach the health endpoint (ADR-041; T-OBS-006). It is reported only by
     * the {@code storage} group, never by {@code /actuator/health} ({@link StorageHealthGroup}).
     */
    @Bean
    HealthIndicator h2DataHealthIndicator(AnonymousSessionShedding shedding) {
        return () -> shedding.shedding() ? Health.down().build() : Health.up().build();
    }

    /** Keeps {@code h2Data} out of {@code /actuator/health}; the {@code storage} group reports it. */
    @Bean
    static HealthEndpointGroupsPostProcessor storageHealthGroup() {
        return new StorageHealthGroup();
    }

}
