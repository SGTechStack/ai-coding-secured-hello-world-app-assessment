package sg.securedhello.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import sg.securedhello.testsupport.Proves;

/** The runner's command line: the trigger, and every refusal before a context starts (ADR-072; ADR-073; ADR-074). */
class RecoveryInvocationTest {

    private static final String DIGEST = "a".repeat(64);

    private static RecoveryInvocation parse(String... args) {
        return RecoveryInvocation.parse(new DefaultApplicationArguments(args));
    }

    @Test
    @Proves("T-RUN-003")
    void onlyTheArgumentTriggersTheRunner() {
        assertThat(RecoveryLauncher.requested("--rebind", "--scope=password")).isTrue();
        assertThat(RecoveryLauncher.requested("--server.port=0")).isFalse();
        // A property spelling of the trigger is not the trigger: only the option itself counts.
        assertThat(RecoveryLauncher.requested("--app.rebind=true", "rebind", "REBIND")).isFalse();
    }

    @Test
    void aDryRunAndAnApplyParse() {
        RecoveryInvocation dryRun = parse("--rebind", "--scope=totp", "--username=alice", "--operator=ops.jane",
                "--spring.datasource.url=jdbc:h2:file:./data/x");
        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.scope()).isEqualTo(RecoveryScope.TOTP);
        assertThat(dryRun.nonInteractive()).isFalse();

        RecoveryInvocation apply = parse("--rebind", "--scope=both", "--batch=accounts.txt", "--operator=ops.jane",
                "--confirm=" + DIGEST, "--reason=mass lockout", "--non-interactive");
        assertThat(apply.dryRun()).isFalse();
        assertThat(apply.batch()).isEqualTo(Path.of("accounts.txt"));
        assertThat(apply.username()).isNull();
        assertThat(apply.nonInteractive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"--password=secret-in-argv", "--new-password=x", "--app.admin.password=x",
            "--Password=x"})
    @Proves("T-RUN-013")
    void noArgumentMayCarryAPassword(String secretArgument) {
        assertThatThrownBy(() -> parse("--rebind", "--scope=password", "--username=alice", "--operator=ops.jane",
                secretArgument))
                .isInstanceOf(RecoveryRefusedException.class)
                .hasMessageContaining("no argument may carry a password")
                .hasMessageNotContaining("secret-in-argv");
    }

    @Test
    void theScopeIsExplicit() {
        assertThatThrownBy(() -> parse("--rebind", "--username=alice", "--operator=ops.jane"))
                .hasMessageContaining("--scope is required");
        assertThatThrownBy(() -> parse("--rebind", "--scope=all", "--username=alice", "--operator=ops.jane"))
                .hasMessageContaining("password, totp or both");
    }

    @Test
    void exactlyOneTargetFormAndAnOperatorAreRequired() {
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--operator=ops.jane"))
                .hasMessageContaining("exactly one of --username and --batch");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=a", "--batch=f", "--operator=o"))
                .hasMessageContaining("exactly one of --username and --batch");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice"))
                .hasMessageContaining("--operator is required");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=jane doe"))
                .hasMessageContaining("--operator is required");
    }

    @Test
    void anApplyNeedsAWellFormedDigestAndAReason() {
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--confirm=" + DIGEST)).hasMessageContaining("go together");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--reason=why")).hasMessageContaining("go together");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--confirm=ABC", "--reason=why")).hasMessageContaining("64-character digest");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--confirm=" + DIGEST, "--reason=" + "x".repeat(257))).hasMessageContaining("1 to 256");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--confirm=" + DIGEST, "--reason= ")).hasMessageContaining("1 to 256");
        assertThat(parse("--rebind", "--scope=totp", "--username=alice", "--operator=o", "--confirm=" + DIGEST,
                "--reason=" + "x".repeat(256)).reason()).hasSize(256);
    }

    @Test
    void unknownOptionsRepeatsAndStrayArgumentsAreRefused() {
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o", "--force"))
                .hasMessageContaining("unknown option --force");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--username=bob",
                "--operator=o")).hasMessageContaining("exactly one value");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o", "extra"))
                .hasMessageContaining("unexpected argument");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--non-interactive=yes")).hasMessageContaining("takes no value");
        assertThatThrownBy(() -> parse("--rebind", "--scope=totp", "--username=alice", "--operator=o",
                "--spring.main.web-application-type=servlet")).hasMessageContaining("sets how the application starts");
    }
}
