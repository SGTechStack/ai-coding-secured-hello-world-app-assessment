package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import javax.sql.DataSource;

/** Reads H2 runtime facts from a live connection, for harness assertions. */
public final class H2Probe {

    private static final String FILE_URL_PREFIX = "jdbc:h2:file:";

    private H2Probe() {
    }

    /** The {@code LOCK_TIMEOUT} the open database actually runs with. */
    public static int lockTimeoutMillis(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT LOCK_TIMEOUT()")) {
            assertThat(rows.next()).as("LOCK_TIMEOUT() result").isTrue();
            return rows.getInt(1);
        }
    }

    /** Asserts the data source is an H2 file database living under the JVM's temporary directory. */
    public static void assertOnTemporaryFile(DataSource dataSource) throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).startsWith(FILE_URL_PREFIX);
            Path directory = Path.of(url.substring(FILE_URL_PREFIX.length()).split(";")[0]).getParent().toRealPath();
            assertThat(directory).startsWith(Path.of(System.getProperty("java.io.tmpdir")).toRealPath());
            try (Stream<Path> files = Files.list(directory)) {
                assertThat(files).anyMatch(file -> file.getFileName().toString().endsWith(".mv.db"));
            }
        }
    }
}
