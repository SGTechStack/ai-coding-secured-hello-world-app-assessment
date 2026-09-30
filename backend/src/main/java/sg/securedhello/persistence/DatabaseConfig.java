package sg.securedhello.persistence;

import java.io.IOException;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** The database file under {@code app.db.data-dir}, and its size gauge (R-OBS-019; IM8 lm-16). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

    /** The gauge of the database file's length in bytes. */
    public static final String FILE_SIZE_GAUGE = "h2.file.size";

    @Bean
    DatabaseFile databaseFile(DatabaseProperties properties, Environment environment) {
        return DatabaseFile.of(properties.dataDir(), environment.getRequiredProperty("spring.datasource.url"));
    }

    /**
     * {@code Files.size} of the database file, read at publish. Unlike a row count it keeps working after an H2 panic;
     * H2's own file-size setting is undocumented, so it is not used. The state object is strongly held.
     */
    @Bean
    MeterBinder databaseFileSizeGauge(DatabaseFile file) {
        return registry -> Gauge.builder(FILE_SIZE_GAUGE, file, DatabaseConfig::bytesOrNaN)
                .description("Length of the H2 database file (ADR-041; R-OBS-019)")
                .baseUnit("bytes")
                .strongReference(true)
                .register(registry);
    }

    private static double bytesOrNaN(DatabaseFile file) {
        try {
            return file.bytes();
        } catch (IOException unreadable) {
            return Double.NaN;
        }
    }
}
