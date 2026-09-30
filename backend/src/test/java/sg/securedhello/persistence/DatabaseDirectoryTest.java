package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.session.shedding.SheddingConfig;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * {@code app.db.data-dir} is the one data directory (R-OBS-019): it holds the database file the open connection uses,
 * and the two storage gauges read it (IM8 lm-16).
 */
class DatabaseDirectoryTest extends CtxDefaultTest {

    @Autowired
    private DatabaseProperties properties;

    @Autowired
    private DatabaseFile databaseFile;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Proves("T-OBS-016")
    void theDataDirectoryHoldsTheDatabaseAndBothGaugesReadIt() throws Exception {
        Path dataDir = properties.dataDir().toAbsolutePath().normalize();
        String resolvedUrl;
        try (Connection connection = dataSource.getConnection()) {
            resolvedUrl = connection.getMetaData().getURL();
        }
        Path database = DatabaseFile.databasePath(resolvedUrl).orElseThrow();
        assertThat(database).as("the open database's file path").startsWithRaw(dataDir);
        assertThat(DatabaseFile.databasePath(environment.getProperty("spring.datasource.url"))).contains(database);
        assertThat(databaseFile.dataDirectory()).isEqualTo(dataDir);
        assertThat(databaseFile.file()).isEqualTo(database.resolveSibling(database.getFileName() + ".mv.db"))
                .exists();

        assertThat(fileSizeGaugeMatchesTheFile()).as("h2.file.size equals the .mv.db length").isTrue();

        double before = anonymousRows();
        CsrfSession.bootstrap(mockMvc);
        System.gc();
        double after = anonymousRows();
        assertThat(after).as("the anonymous-row gauge after a GC").isNotNaN().isEqualTo(before + 1);
        assertThat(after).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NULL", Long.class).doubleValue());
    }

    /** The file can grow between two reads while H2 writes in the background, so a stable read is looked for. */
    private boolean fileSizeGaugeMatchesTheFile() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            long before = Files.size(databaseFile.file());
            double gauge = registry.get(DatabaseConfig.FILE_SIZE_GAUGE).gauge().value();
            long after = Files.size(databaseFile.file());
            if (before == after && gauge == before) {
                return true;
            }
        }
        return false;
    }

    private double anonymousRows() {
        return registry.get(SheddingConfig.ANONYMOUS_ROWS_GAUGE).gauge().value();
    }
}
