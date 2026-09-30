package sg.securedhello.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The H2 database file and the data directory that holds it (R-OBS-019): the file named by
 * {@code spring.datasource.url}, under {@code app.db.data-dir}. Sessions, accounts and audit data share it (ADR-041).
 *
 * @param dataDirectory the data directory, absolute and normalised
 * @param file          the database file, {@code <path>.mv.db}, absolute and normalised
 */
public record DatabaseFile(Path dataDirectory, Path file) {

    private static final String H2_FILE_PREFIX = "jdbc:h2:file:";

    /** H2's MVStore file suffix. */
    static final String MV_STORE_SUFFIX = ".mv.db";

    /** The database file {@code url} names, under {@code dataDir}. */
    static DatabaseFile of(Path dataDir, String url) {
        Path database = databasePath(url).orElseThrow(() -> new IllegalStateException(
                "spring.datasource.url is not an H2 file database (ADR-051)"));
        return new DatabaseFile(absolute(dataDir), database.resolveSibling(database.getFileName() + MV_STORE_SUFFIX));
    }

    /**
     * The database path of an H2 file URL, without H2's file suffix, absolute and normalised; {@code ~} is the user's
     * home, as in H2. Empty for any other URL.
     */
    public static Optional<Path> databasePath(String url) {
        if (url == null || !url.trim().toLowerCase(Locale.ROOT).startsWith(H2_FILE_PREFIX)) {
            return Optional.empty();
        }
        String file = url.trim().substring(H2_FILE_PREFIX.length()).split(";", 2)[0];
        if (file.startsWith("~")) {
            file = System.getProperty("user.home") + file.substring(1);
        }
        return Optional.of(absolute(Path.of(file)));
    }

    /**
     * Whether an H2 file URL's database file lies in {@code dataDir} or below it; any other URL has nothing to check.
     * The file is {@code <path>.mv.db}, so it is the path's directory that must lie under the data directory: a path
     * equal to the data directory names a file beside it, not in it.
     */
    public static boolean underDataDirectory(String url, Path dataDir) {
        return databasePath(url).map(database -> database.getParent() != null
                && database.getParent().startsWith(absolute(dataDir))).orElse(true);
    }

    /** The file's length now, as {@code Files.size} reports it. */
    public long bytes() throws IOException {
        return Files.size(file);
    }

    /** The bytes a new write can use now on the data directory's volume. */
    public long usableBytes() throws IOException {
        return Files.getFileStore(dataDirectory).getUsableSpace();
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
