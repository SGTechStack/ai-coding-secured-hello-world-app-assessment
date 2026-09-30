package sg.securedhello.recovery;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.WebApplicationContext;

/**
 * The runner's preconditions, applied to the application's own context before it refreshes (ADR-072; REJ-089). The
 * context still runs its full refresh, so the refresh-phase configuration validator runs too (T-RUN-004):
 * <ul>
 *   <li>the audit file appender is detached until the runner's checks pass ({@link AuditFileGate});</li>
 *   <li>the datasource URL gets {@code IFEXISTS=TRUE}, outranking every other source, so a wrong path is refused and
 *       no database file is created (T-RUN-005);</li>
 *   <li>Flyway never migrates: a database whose schema is not exactly this jar's is refused, naming both versions,
 *       before Hibernate validates anything (T-RUN-006);</li>
 *   <li>the context is never a web application, so the web-only startup beans, the bootstrap seeder and the
 *       session reconciliation sweep, are not beans at all: it neither seeds (T-RUN-007) nor sweeps.</li>
 * </ul>
 */
final class RunnerMode implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String PROPERTY_SOURCE = "recoveryRunner";

    private static final String DATASOURCE_URL = "spring.datasource.url";

    private static final Pattern IFEXISTS = Pattern.compile("(?i);IFEXISTS=[^;]*");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (context instanceof WebApplicationContext) {
            // spring.main.web-application-type can still arrive from the environment or a config file.
            throw new IllegalStateException("The runner never starts a web server (ADR-072): remove any"
                    + " spring.main.web-application-type setting");
        }
        AuditFileGate.detach();
        String url = context.getEnvironment().getProperty(DATASOURCE_URL);
        if (url == null) {
            throw new IllegalStateException(DATASOURCE_URL + " is not set; the runner opens only an existing"
                    + " database file");
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE,
                Map.of(DATASOURCE_URL, ifExists(url))));
        GenericApplicationContext generic = (GenericApplicationContext) context;
        generic.registerBean(FlywayMigrationStrategy.class, () -> RunnerMode::refuseUnlessCurrent);
    }

    /** {@code url} opening only an existing database: any {@code IFEXISTS} it had is replaced. */
    static String ifExists(String url) {
        return IFEXISTS.matcher(url).replaceAll("") + ";IFEXISTS=TRUE";
    }

    /** Stands in for Flyway's migrate: checks the schema is this jar's and changes nothing. */
    static void refuseUnlessCurrent(Flyway flyway) {
        MigrationInfoService info = flyway.info();
        MigrationInfo current = info.current();
        // Applied but not in this jar: the database is newer than the jar, or was migrated by another.
        boolean unknown = Stream.of(info.applied()).anyMatch(migration -> !migration.getState().isResolved());
        if (info.pending().length > 0 || unknown) {
            throw new IllegalStateException("The database schema is at version "
                    + (current == null ? "none" : current.getVersion().getVersion()) + " and this jar's is "
                    + jarVersion(info) + ". The runner never migrates: run it from the jar that matches the deployed"
                    + " application (R-RUN-005)");
        }
        // Every applied migration is this jar's: their checksums must match too, as a migrate would check.
        if (!flyway.validateWithResult().validationSuccessful) {
            throw new IllegalStateException("The database's applied migrations do not match this jar's (a checksum or"
                    + " a failed migration). The runner never repairs or migrates (R-RUN-005)");
        }
    }

    private static String jarVersion(MigrationInfoService info) {
        return Stream.of(info.all())
                .filter(migration -> migration.getState().isResolved() && migration.getVersion() != null)
                .map(MigrationInfo::getVersion)
                .max(Comparable::compareTo)
                .map(MigrationVersion::getVersion)
                .orElse("none");
    }
}
