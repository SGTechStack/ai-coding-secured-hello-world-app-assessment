package sg.securedhello.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.RecoveryRunContext;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccountRepository;

/**
 * The recovery runner's decisions on the shared context: the same {@link RecoveryRunner} the launcher runs, with its
 * streams in memory. The process itself (the launcher, the refresh-phase preconditions, the real streams and the
 * audit file) is {@code RecoveryRunnerProcessIT}. The shared database accumulates admins, so enrolled-admin counts
 * are asserted as differences.
 */
class RecoveryRunnerTest extends CtxDefaultTest {

    private static final String NEW_PASSWORD = "copper lantern drifts eastward";
    private static final RecoveryRunContext.Operator OPERATOR =
            new RecoveryRunContext.Operator("ops.jane", "svc-runner", "host-1", "/srv/app");
    private static final Pattern DIGEST = Pattern.compile("digest:\\s+([0-9a-f]{64})");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private Tombstones tombstones;

    @Autowired
    private UserAccountRepository userAccounts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private RecoveryRunner runner;
    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        runner = context.getAutowireCapableBeanFactory().createBean(RecoveryRunner.class);
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    /** One run's exit status and output. */
    private record Run(int status, String out, String err) {

        String digest() {
            Matcher matcher = DIGEST.matcher(out);
            assertThat(matcher.find()).as("a digest in %s", out).isTrue();
            return matcher.group(1);
        }
    }

    private Run run(RecoveryRunContext.Operator operator, String stdin, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        RecoveryInvocation invocation = RecoveryInvocation.parse(new DefaultApplicationArguments(
                Stream.concat(Stream.of("--rebind"), Stream.of(args)).toArray(String[]::new)));
        int status = runner.run(invocation, operator, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Run run(String stdin, String... args) {
        return run(OPERATOR, stdin, args);
    }

    private Run dryRun(String scope, String target) {
        return run("", "--scope=" + scope, target, "--operator=ops.jane");
    }

    private Run apply(String scope, String target, String digest, String stdin) {
        return run(stdin, "--scope=" + scope, target, "--operator=ops.jane", "--confirm=" + digest,
                "--reason=sole admin lost the phone", "--non-interactive");
    }

    private Map<String, Object> user(UUID id) {
        return jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id);
    }

    private int factorRows(UUID id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM totp_user_details WHERE user_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }

    private void capAndLock(Account account) {
        jdbc.update("UPDATE users SET failed_login_attempts = 5, locked_until = ?, "
                        + "consecutive_failures_since_success = 100, password_disabled_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), Timestamp.from(clock.instant()),
                account.id());
    }

    private static long count(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    @Test
    @Proves("T-RUN-011")
    void aDryRunChangesNothingPrintsTheDigestAndWritesItsOwnRow() {
        Account admin = accounts.withRole("ADMIN");
        Map<String, Object> before = user(admin.id());
        try (AuditCapture audit = AuditCapture.start()) {
            Run run = dryRun("password", "--username=" + admin.username());

            assertThat(run.status()).isZero();
            assertThat(run.out()).contains("dry run; nothing changed", admin.id().toString(), "schema version:");
            assertThat(user(admin.id())).isEqualTo(before);
            assertThat(audit.rows()).hasSize(1);
            assertThat(audit.withMessage("Recovery runner dry run.")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.action", "user-administration")
                    .containsEntry("event.outcome", "success")
                    .containsEntry("labels.runner_digest", run.digest())
                    .containsEntry("labels.runner_scope", "password")
                    .containsEntry("labels.operator_claimed_id", "ops.jane")
                    .containsEntry("process.real_user.name", "svc-runner")
                    .containsEntry("user.target.id", admin.id().toString())
                    .containsEntry("user.target.count", 1)
                    .containsKeys("labels.enrolled_admins_before", "labels.database_path",
                            "labels.database_schema_version", "labels.database_modified", "labels.working_directory")
                    .doesNotContainKeys("labels.runner_reason", "labels.enrolled_admins_after"));
        }
        // The same state gives the same digest; the operator is not part of it (ADR-074).
        Run again = run(new RecoveryRunContext.Operator("ops.other", null, "host-2", "/tmp"), "",
                "--scope=password", "--username=" + admin.username(), "--operator=ops.other");
        assertThat(again.digest()).isEqualTo(dryRun("password", "--username=" + admin.username()).digest());
    }

    @Test
    @Proves("T-RUN-011")
    void aWrongOrStaleDigestIsRefusedAndChangesNothing() {
        Account admin = accounts.withRole("ADMIN");
        String digest = dryRun("password", "--username=" + admin.username()).digest();
        Map<String, Object> before = user(admin.id());
        String wrong = (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1);

        try (AuditCapture audit = AuditCapture.start()) {
            Run refused = apply("password", "--username=" + admin.username(), wrong, NEW_PASSWORD + "\n");
            assertThat(refused.status()).isEqualTo(RecoveryRunner.REFUSED);
            assertThat(refused.err()).contains("no longer matches the --confirm digest");
            // The state moves between plan and apply: a sign-in failure stamps the lockout columns.
            capAndLock(admin);
            Run stale = apply("password", "--username=" + admin.username(), digest, NEW_PASSWORD + "\n");
            assertThat(stale.status()).isEqualTo(RecoveryRunner.REFUSED);
            assertThat(audit.rows()).isEmpty();
        }
        assertThat(user(admin.id()).get("PASSWORD_HASH")).isEqualTo(before.get("PASSWORD_HASH"));
    }

    @Test
    @Proves("T-RUN-011")
    void passwordScopeSetsAForcedChangeCredentialTheAdminSignsInWithAndALeftoverConfirmFiresOnce()
            throws Exception {
        Account admin = accounts.withRole("ADMIN");
        capAndLock(admin);
        String digest = dryRun("password", "--username=" + admin.username()).digest();

        try (AuditCapture audit = AuditCapture.start()) {
            Run applied = apply("password", "--username=" + admin.username(), digest, NEW_PASSWORD + "\n");

            assertThat(applied.status()).as(applied.err()).isZero();
            assertThat(applied.out()).contains("applied", admin.id().toString()).doesNotContain(NEW_PASSWORD);
            assertThat(audit.rows()).extracting(row -> row.get("message"))
                    .containsExactly("Recovery runner apply started.", "Recovery runner apply completed.");
            Map<String, Object> started = audit.rows().getFirst();
            Map<String, Object> completed = audit.rows().getLast();
            assertThat(started).containsEntry("event.outcome", "unknown")
                    .containsEntry("labels.runner_reason", "sole admin lost the phone")
                    .containsEntry("labels.runner_digest", digest)
                    .doesNotContainKey("labels.enrolled_admins_after");
            assertThat(completed).containsEntry("event.outcome", "success")
                    .containsEntry("user.target.id", admin.id().toString());
            assertThat(count(completed, "labels.enrolled_admins_after"))
                    .isEqualTo(count(completed, "labels.enrolled_admins_before"));
            assertThat(audit.rows().toString()).doesNotContain(NEW_PASSWORD);
        }
        Map<String, Object> row = user(admin.id());
        assertThat(row).containsEntry("FORCE_PASSWORD_CHANGE", true).containsEntry("PASSWORD_DISABLED_AT", null)
                .containsEntry("LOCKED_UNTIL", null).containsEntry("CONSECUTIVE_FAILURES_SINCE_SUCCESS", 0);
        assertThat(row.get("CREDENTIAL_ISSUED_AT")).isNotNull();
        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), admin.username(), NEW_PASSWORD)
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true));

        // The same invocation left behind: applying moved the state, so it never fires again.
        Run leftover = apply("password", "--username=" + admin.username(), digest, "another secret phrase here\n");
        assertThat(leftover.status()).isEqualTo(RecoveryRunner.REFUSED);
    }

    @Test
    @Proves("T-RUN-009")
    void totpScopeClearsTheFactorOfAHealthyEnrolledAdminAndAuditsTheEnrolledCount() {
        Account admin = accounts.withRole("ADMIN");
        factors.enrol(admin);
        String digest = dryRun("totp", "--username=" + admin.username()).digest();

        try (AuditCapture audit = AuditCapture.start()) {
            Run applied = apply("totp", "--username=" + admin.username(), digest, "");

            assertThat(applied.status()).as(applied.err()).isZero();
            Map<String, Object> completed = audit.withMessage("Recovery runner apply completed.").getFirst();
            assertThat(count(completed, "labels.enrolled_admins_after"))
                    .isEqualTo(count(completed, "labels.enrolled_admins_before") - 1);
        }
        assertThat(factorRows(admin.id())).isZero();
        // The password is untouched: totp scope rebinds the factor only.
        assertThat(user(admin.id())).containsEntry("FORCE_PASSWORD_CHANGE", false);
    }

    @Test
    void bothScopeClearsTheFactorAndSetsThePassword() {
        Account admin = accounts.withRole("ADMIN");
        factors.enrol(admin);
        String digest = dryRun("both", "--username=" + admin.username()).digest();

        Run applied = apply("both", "--username=" + admin.username(), digest, NEW_PASSWORD + "\n");

        assertThat(applied.status()).as(applied.err()).isZero();
        assertThat(factorRows(admin.id())).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, (String) user(admin.id()).get("PASSWORD_HASH"))).isTrue();
    }

    @Test
    @Proves("T-RUN-008")
    void aMissingOrTombstonedAccountOrAMissingFactorIsRefused() {
        Account deleted = accounts.user();
        Account admin = accounts.withRole("ADMIN");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                tombstones.deleteLeavingTombstone(userAccounts.findById(deleted.id()).orElseThrow(), admin.id()));

        Run missing = dryRun("password", "--username=" + Accounts.unknownUsername());
        Run tombstoned = dryRun("password", "--username=" + deleted.username());
        Run noFactor = dryRun("totp", "--username=" + admin.username());
        Run bothNoFactor = dryRun("both", "--username=" + admin.username());

        assertThat(List.of(missing, tombstoned, noFactor, bothNoFactor)).allSatisfy(run -> {
            assertThat(run.status()).isEqualTo(RecoveryRunner.REFUSED);
            assertThat(run.out()).doesNotContain("digest");
        });
        assertThat(missing.err()).contains("no account has the username");
        assertThat(tombstoned.err()).contains("names a deleted account");
        assertThat(noFactor.err()).contains("has no TOTP factor");
    }

    @Test
    void aPasswordThePolicyRefusesRollsBackAndWritesTheFailedRow() {
        Account admin = accounts.withRole("ADMIN");
        capAndLock(admin);
        String digest = dryRun("password", "--username=" + admin.username()).digest();

        try (AuditCapture audit = AuditCapture.start()) {
            Run rejected = apply("password", "--username=" + admin.username(), digest, "short\n");

            assertThat(rejected.status()).isEqualTo(RecoveryRunner.FAILED);
            assertThat(rejected.err()).contains("rule").doesNotContain("short");
            assertThat(audit.rows()).extracting(row -> row.get("message"))
                    .containsExactly("Recovery runner apply started.", "Recovery runner apply failed.");
            assertThat(audit.rows().getLast()).containsEntry("event.outcome", "failure");
        }
        // Rolled back: the cap is still there.
        assertThat(user(admin.id()).get("PASSWORD_DISABLED_AT")).isNotNull();
    }

    @Test
    void anEmptyStdinRefusesBeforeAnyRow() {
        Account admin = accounts.withRole("ADMIN");
        String digest = dryRun("password", "--username=" + admin.username()).digest();

        try (AuditCapture audit = AuditCapture.start()) {
            Run run = apply("password", "--username=" + admin.username(), digest, "");

            assertThat(run.status()).isEqualTo(RecoveryRunner.REFUSED);
            assertThat(run.err()).contains("stdin had none");
            assertThat(audit.rows()).isEmpty();
        }
    }

    @Test
    @Proves("T-RUN-011")
    void aBatchIsBoundToEveryAccountsStateAndMintsNothing(@TempDir Path directory) throws IOException {
        Account first = accounts.user();
        Account second = accounts.user();
        capAndLock(first);
        Path input = Files.writeString(directory.resolve("accounts.txt"),
                first.username() + "\n\n  " + second.username() + "  \n");
        String batch = "--batch=" + input;
        String digest = dryRun("password", batch).digest();

        // One account moves between plan and apply: the whole confirm is invalid.
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                second.id());
        Run stale = apply("password", batch, digest, "");
        assertThat(stale.status()).isEqualTo(RecoveryRunner.REFUSED);
        assertThat(user(first.id()).get("PASSWORD_HASH")).isNotNull();

        String fresh = dryRun("password", batch).digest();
        try (AuditCapture audit = AuditCapture.start()) {
            Run applied = apply("password", batch, fresh, "");

            assertThat(applied.status()).as(applied.err()).isZero();
            assertThat(audit.withMessage("Recovery runner apply completed.")).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("user.target.count", 2)
                            .doesNotContainKey("user.target.id"));
        }
        for (Account account : List.of(first, second)) {
            assertThat(user(account.id())).containsEntry("PASSWORD_HASH", null)
                    .containsEntry("PASSWORD_DISABLED_AT", null).containsEntry("FORCE_PASSWORD_CHANGE", false);
            Integer tokens = jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens WHERE user_id = ?",
                    Integer.class, account.id());
            assertThat(tokens).as("the batch form mints nothing").isZero();
        }
        assertThat(apply("password", batch, fresh, "").status()).isEqualTo(RecoveryRunner.REFUSED);
    }

    @Test
    void aBatchFileSavedWithAByteOrderMarkNamesItsFirstAccountUnchanged(@TempDir Path directory) throws IOException {
        Account first = accounts.user();
        Account second = accounts.user();
        Path input = Files.writeString(directory.resolve("bom.txt"),
                "﻿" + first.username() + "\n" + second.username() + "\n");

        Run planned = dryRun("password", "--batch=" + input);

        assertThat(planned.status()).as(planned.err()).isZero();
        assertThat(planned.out()).contains(first.id().toString(), second.id().toString());
    }

    @Test
    void aBatchOverItsCapOrNamingAnInadmissibleAccountIsRefusedAsAWhole(@TempDir Path directory)
            throws IOException {
        Account kept = accounts.user();
        Path tooMany = Files.write(directory.resolve("many.txt"), IntStream.rangeClosed(0, RecoveryRunner.BATCH_CAP)
                .mapToObj(i -> "user" + i).toList());
        Path mixed = Files.writeString(directory.resolve("mixed.txt"),
                kept.username() + "\n" + Accounts.unknownUsername() + "\n");
        // The same account twice, once spelt in upper case: duplicates are found after canonicalisation.
        Path repeated = Files.writeString(directory.resolve("repeated.txt"),
                kept.username() + "\n" + kept.username().toUpperCase(java.util.Locale.ROOT) + "\n");
        Path nothingToInvalidate = Files.writeString(directory.resolve("invalidated.txt"),
                accounts.notActivated().username() + "\n");

        assertThat(dryRun("password", "--batch=" + tooMany).err()).contains("1 to " + RecoveryRunner.BATCH_CAP);
        assertThat(dryRun("password", "--batch=" + mixed).err()).contains("refused as a whole", "no account has");
        assertThat(dryRun("password", "--batch=" + repeated).err()).contains("more than once");
        assertThat(dryRun("password", "--batch=" + nothingToInvalidate).err()).contains("no password to invalidate");
        assertThat(dryRun("password", "--batch=" + directory.resolve("absent.txt")).status())
                .isEqualTo(RecoveryRunner.REFUSED);
    }

    @Test
    @Proves("T-RUN-001")
    void anUnreportedOsUserIsRecordedAsAbsent() {
        Account admin = accounts.withRole("ADMIN");
        RecoveryRunContext.Operator unreported = RecoveryLauncher.operator("ops.jane", new NoUserInfo());
        assertThat(unreported.realUser()).isNull();

        try (AuditCapture audit = AuditCapture.start()) {
            run(unreported, "", "--scope=password", "--username=" + admin.username(), "--operator=ops.jane");

            assertThat(audit.withMessage("Recovery runner dry run.")).singleElement().satisfies(row -> assertThat(row)
                    .doesNotContainKey("process.real_user.name")
                    .doesNotContainValue(System.getProperty("user.name")));
        }
    }

    /** A process whose OS user the JDK cannot report. */
    private static final class NoUserInfo implements ProcessHandle.Info {

        @Override
        public java.util.Optional<String> command() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> commandLine() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String[]> arguments() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.time.Instant> startInstant() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Duration> totalCpuDuration() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> user() {
            return java.util.Optional.empty();
        }
    }
}
