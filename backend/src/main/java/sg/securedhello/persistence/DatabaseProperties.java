package sg.securedhello.persistence;

import java.nio.file.Path;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.db.*}: where the H2 database lives (R-OBS-019).
 *
 * @param dataDir the directory holding the database file, which the datasource URL's file path must lie under (the
 *                startup validator refuses a mismatch). The shed check's free space and the file-size gauge read it,
 *                so it is the one source of the data directory
 */
@Validated
@ConfigurationProperties("app.db")
public record DatabaseProperties(@NotNull Path dataDir) {
}
