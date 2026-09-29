package sg.securedhello.session.shedding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.audit.AuditEmitter;

/** Anonymous-session shedding and the {@code h2Data} health indicator that shares its state (ADR-041). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SheddingProperties.class)
public class SheddingConfig {

    private static final String H2_FILE_PREFIX = "jdbc:h2:file:";

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

    /** The volume of the directory holding the H2 file that {@code spring.datasource.url} names. */
    @Bean
    SessionStoreVolume sessionStoreVolume(Environment environment) {
        Path directory = databaseDirectory(environment.getRequiredProperty("spring.datasource.url"));
        // Looked up on each read: the directory may not exist until H2 creates the file, and a failed read sheds.
        return () -> Files.getFileStore(directory).getUsableSpace();
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

    /**
     * The directory holding the database file of an H2 file URL; {@code ~} is the user's home, as in H2. The startup
     * validator has already refused anything but an H2 file URL (ADR-051).
     */
    static Path databaseDirectory(String url) {
        if (!url.toLowerCase(Locale.ROOT).startsWith(H2_FILE_PREFIX)) {
            throw new IllegalStateException("spring.datasource.url is not an H2 file database (ADR-051)");
        }
        String file = url.substring(H2_FILE_PREFIX.length()).split(";", 2)[0];
        if (file.startsWith("~")) {
            file = System.getProperty("user.home") + file.substring(1);
        }
        return Path.of(file).toAbsolutePath().normalize().getParent();
    }
}
