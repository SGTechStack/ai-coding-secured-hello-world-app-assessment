package sg.securedhello.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RunnerProcess;
import sg.securedhello.testsupport.TestSecrets;

/**
 * The recovery runner as a process (level R, {@code runner}): the application's own main class, started with
 * {@code --rebind} in a JVM of its own, against a database a normal start created and then released. What only a
 * process shows is here: the real streams, the audit file, the environment, the H2 file lock and the refresh-phase
 * preconditions. The runner's decisions are {@link RecoveryRunnerTest}.
 */
class RecoveryRunnerProcessIT {

    /** The password the operator supplies: a canary, scanned for in every stream and file the process writes. */
    private static final String OPERATOR_PASSWORD = "TEST-ONLY-CANARY-runner-granite-41";
    private static final String ADMIN = TestSecrets.ADMIN_USERNAME;
    private static final Pattern DIGEST = Pattern.compile("digest:\\s+([0-9a-f]{64})");

    @TempDir
    static Path shared;

    /** A database a normal start migrated and seeded; the tests that use it never change it. */
    private static Path sharedDatabase;

    @TempDir
    Path directory;

    @BeforeAll
    static void createTheSharedDatabase() {
        sharedDatabase = createdDatabase(shared.resolve("db"));
    }

    /** A database at {@code directory}, migrated and holding the seeded bootstrap admin, released. */
    private static Path createdDatabase(Path directory) {
        RestartHarness.Boot boot = RestartHarness.boot(builder -> builder.initializers(
                RestartHarness.onDatabase(directory)));
        assertThat(boot.failure()).isNull();
        return directory.resolve("secured-hello");
    }

    private RunnerProcess runner(Path database) {
        return RunnerProcess.on(database, directory.resolve("audit"), directory.resolve("work"));
    }

    private static String[] args(String... runnerArguments) {
        return Stream.concat(Stream.of("--rebind", "--operator=ops.jane"), Stream.of(runnerArguments))
                .toArray(String[]::new);
    }

    private static String digest(RunnerProcess.Result dryRun) {
        Matcher matcher = DIGEST.matcher(dryRun.stdout());
        assertThat(matcher.find()).as("a digest in %s%s", dryRun.stdout(), dryRun.stderr()).isTrue();
        return matcher.group(1);
    }

    private String auditFile() throws IOException {
        Path file = directory.resolve("audit").resolve("audit.ndjson");
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    /** Runs {@code query} on the released database file, as the only connection. */
    private static <T> T onDatabase(Path database, java.util.function.Function<JdbcTemplate, T> query) {
        try (Connection connection = DriverManager.getConnection(RunnerProcess.url(database) + ";IFEXISTS=TRUE",
                "", "")) {
            return query.apply(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static String passwordHash(Path database) {
        return onDatabase(database, jdbc -> jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?",
                String.class, ADMIN));
    }

    private static int sessionsOf(Path database, String username) {
        return onDatabase(database, jdbc -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, username));
    }

    @Test
    @Proves({"T-AUD-027", "T-AUD-029", "T-RUN-002", "T-RUN-011"})
    void aRunWritesItsThreeRowsNoSecretAndALeftoverConfirmFiresOnce() throws IOException {
        Path database = createdDatabase(directory.resolve("db"));
        RunnerProcess runner = runner(database);

        RunnerProcess.Result dryRun = runner.run("", args("--scope=password", "--username=" + ADMIN));
        assertThat(dryRun.exitCode()).as(dryRun.stderr()).isZero();
        String digest = digest(dryRun);
        // A session the admin left behind, which the rebinding must end even with no web server (ADR-037).
        onDatabase(database, jdbc -> jdbc.update("INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME,"
                + " LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME) VALUES (?, ?, 0, 0, 900,"
                + " 9999999999999, ?)", "p-" + digest.substring(0, 8), "s-" + digest.substring(0, 8), ADMIN));
        assertThat(sessionsOf(database, ADMIN)).isOne();
        String[] apply = args("--scope=password", "--username=" + ADMIN, "--confirm=" + digest,
                "--reason=rehearsal", "--non-interactive");
        RunnerProcess.Result applied = runner.run(OPERATOR_PASSWORD + "\n", apply);
        assertThat(applied.exitCode()).as(applied.stderr()).isZero();
        assertThat(applied.stdout()).contains("Recovery runner: applied.");
        // The web-only startup beans are absent: no seeding, and no reconciliation sweep beside the previewed change.
        for (RunnerProcess.Result run : List.of(dryRun, applied)) {
            assertThat(run.stdout()).doesNotContain("Administrator bootstrap", "Sessions reconciled at startup.");
        }
        assertThat(sessionsOf(database, ADMIN)).as("the rebound account's sessions ended").isZero();

        // T-AUD-027: the password crosses no stream, and no other secret does either.
        List<String> secrets = Stream.concat(Stream.of(OPERATOR_PASSWORD), TestSecrets.CANARIES.stream()).toList();
        for (String output : List.of(dryRun.stdout(), dryRun.stderr(), applied.stdout(), applied.stderr(),
                auditFile())) {
            assertThat(output).doesNotContain(secrets);
        }
        // REJ-090: the dry-run, intent and outcome rows reach the audit file, with the counts before and after.
        List<Map<String, Object>> rows = EcsJson.rowsWithAction(auditFile(), "user-administration");
        assertThat(rows).extracting(row -> row.get("message")).containsExactly("Recovery runner dry run.",
                "Recovery runner apply started.", "Recovery runner apply completed.");
        assertThat(rows.getLast()).containsKeys("labels.enrolled_admins_before", "labels.enrolled_admins_after")
                .containsEntry("labels.operator_claimed_id", "ops.jane")
                .containsEntry("labels.database_path",
                        database.getParent().toRealPath().resolve(database.getFileName()).toString());
        String osUser = ProcessHandle.current().info().user().orElseThrow();
        assertThat(rows).allSatisfy(row -> assertThat(row)
                // T-RUN-002: the OS user as the JDK reports it for the runner process (the same user as this one).
                .containsEntry("process.real_user.name", osUser)
                // T-AUD-029: no command line, so a mistyped secret in the arguments never reaches the audit stream.
                .doesNotContainKeys("process.command_line", "process.args", "process.args_count"));
        assertThat(auditFile()).doesNotContain("--rebind", "--operator");

        // T-RUN-011: the same invocation, left behind in a unit file, is refused on every later start.
        RunnerProcess.Result leftover = runner.run(OPERATOR_PASSWORD + "\n", apply);
        assertThat(leftover.exitCode()).isEqualTo(RecoveryRunner.REFUSED);
        assertThat(leftover.stderr()).contains("no longer matches the --confirm digest");
    }

    @Test
    @Proves("T-RUN-003")
    void aTriggerInTheEnvironmentFiresNothing() {
        String before = passwordHash(sharedDatabase);

        RunnerProcess.Result started = runner(sharedDatabase).env("REBIND", "true").env("APP_REBIND", "true")
                .runUntil(out -> out.contains("\"application-startup\""), OPERATOR_PASSWORD + "\n",
                        "--server.port=0", "--scope=password", "--username=" + ADMIN, "--operator=ops.jane",
                        "--non-interactive");

        // The application started as itself, with its startup sweep; the runner never ran.
        assertThat(started.stdout()).doesNotContain("Recovery runner").contains("Sessions reconciled at startup.");
        assertThat(passwordHash(sharedDatabase)).isEqualTo(before);
    }

    @Test
    @Proves("T-RUN-003")
    void anEmptyStdinOrNoTerminalRefusesAndNeverWaits() {
        RunnerProcess runner = runner(sharedDatabase);
        String digest = digest(runner.run("", args("--scope=password", "--username=" + ADMIN)));

        RunnerProcess.Result endOfInput = runner.run("", args("--scope=password", "--username=" + ADMIN,
                "--confirm=" + digest, "--reason=rehearsal", "--non-interactive"));
        // Interactive, with stdin piped: there is no console, so it refuses rather than read what is piped in.
        RunnerProcess.Result noTerminal = runner.run(OPERATOR_PASSWORD + "\n", args("--scope=password",
                "--username=" + ADMIN, "--confirm=" + digest, "--reason=rehearsal"));

        assertThat(endOfInput.exitCode()).isEqualTo(RecoveryRunner.REFUSED);
        assertThat(endOfInput.stderr()).contains("stdin had none");
        assertThat(noTerminal.exitCode()).isEqualTo(RecoveryRunner.REFUSED);
        assertThat(noTerminal.stderr()).contains("no terminal to prompt on");
    }

    @Test
    @Proves("T-RUN-004")
    void theRunnerRunsTheRefreshPhaseValidator() {
        RunnerProcess.Result refused = runner(sharedDatabase)
                .property("spring.datasource.url", RunnerProcess.url(sharedDatabase) + ";AUTO_SERVER=TRUE")
                .run("", args("--scope=password", "--username=" + ADMIN));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused.stderr()).contains("prohibited configuration", "AUTO_SERVER");
    }

    @Test
    void aWebApplicationTypeFromTheEnvironmentIsRefused() {
        RunnerProcess.Result refused = runner(sharedDatabase).env("SPRING_MAIN_WEBAPPLICATIONTYPE", "servlet")
                .run("", args("--scope=password", "--username=" + ADMIN));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused.stderr()).contains("never starts a web server");
        assertThat(refused.stdout()).doesNotContain("Tomcat started");
    }

    @Test
    @Proves("T-RUN-005")
    void aWrongDatabasePathIsRefusedAndCreatesNoFile() {
        Path missing = directory.resolve("nowhere").resolve("secured-hello");

        RunnerProcess.Result refused = runner(missing).run("", args("--scope=password", "--username=" + ADMIN));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused.stderr()).contains("not found");
        assertThat(directory.resolve("nowhere")).doesNotExist();
    }

    @Test
    @Proves("T-RUN-006")
    void anOlderSchemaIsRefusedNamingBothVersionsAndIsNotMigrated() {
        Path database = directory.resolve("old").resolve("secured-hello");
        Flyway.configure().dataSource(RunnerProcess.url(database), "", "").target("5").load().migrate();

        RunnerProcess.Result refused = runner(database).run("", args("--scope=password", "--username=" + ADMIN));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused.stderr()).containsPattern("schema is at version 5 and this jar's is [0-9]+\\.")
                .contains("never migrates").doesNotContain("this jar's is 5.");
        assertThat(Flyway.configure().dataSource(RunnerProcess.url(database), "", "").load().info().current()
                .getVersion().getVersion()).isEqualTo("5");
    }

    @Test
    @Proves("T-RUN-007")
    void anEmptyDatabaseIsNeitherMigratedNorSeeded() throws SQLException {
        Path database = directory.resolve("empty").resolve("secured-hello");
        DriverManager.getConnection(RunnerProcess.url(database), "", "").close();

        RunnerProcess.Result refused = runner(database).run("", args("--scope=password", "--username=" + ADMIN));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused.stderr()).contains("schema is at version none");
        try (Connection connection = DriverManager.getConnection(RunnerProcess.url(database), "", "")) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", Integer.class))
                    .as("no table was created, so nothing was migrated or seeded").isZero();
        }
    }

    @Test
    @Proves("T-RUN-010")
    void withTheApplicationRunningTheRunnerExitsAndAppendsNothingToTheAuditFile() throws IOException {
        Path audit = Files.createDirectories(directory.resolve("audit")).resolve("audit.ndjson");
        Files.writeString(audit, "{\"message\":\"an earlier row\"}\n");
        byte[] before = Files.readAllBytes(audit);
        RunnerProcess.Result[] refused = new RunnerProcess.Result[1];

        RestartHarness.Boot boot = RestartHarness.run(builder -> builder.initializers(
                RestartHarness.onDatabase(sharedDatabase.getParent())), context -> refused[0] =
                runner(sharedDatabase).run("", args("--scope=password", "--username=" + ADMIN)));

        assertThat(boot.failure()).isNull();
        assertThat(refused[0].exitCode()).isEqualTo(RecoveryLauncher.NOT_STARTED);
        assertThat(refused[0].stderr()).contains("The file is locked");
        assertThat(Files.readAllBytes(audit)).isEqualTo(before);
    }

    @Test
    @Proves("T-RUN-013")
    void aPasswordArgumentIsRefusedBeforeAnythingStarts() {
        RunnerProcess.Result refused = runner(sharedDatabase).run("", args("--scope=password",
                "--username=" + ADMIN, "--password=" + OPERATOR_PASSWORD));

        assertThat(refused.exitCode()).isEqualTo(RecoveryLauncher.INVALID);
        assertThat(refused.stderr()).contains("no argument may carry a password");
        assertThat(refused.stdout() + refused.stderr()).doesNotContain(OPERATOR_PASSWORD);
    }
}
