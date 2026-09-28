package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import sg.securedhello.audit.AuditEmitter;

/** The suite-wide canary-secret scan and degraded-row check, which {@link LogOutputGuard} runs after every test. */
class LogOutputGuardTest {

    static final List<String> CANARIES = TestSecrets.CANARIES;

    @Test
    @Proves("T-AUD-013")
    void theGuardSeesEverythingWrittenToStdoutAndStderr() {
        assertThat(LogOutputGuard.capturesStandardStreams()).isTrue();
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }

    @ParameterizedTest
    @FieldSource("CANARIES")
    @Proves("T-AUD-013")
    void everyCanaryIsFoundInEveryEncodedForm(String canary) {
        assertThat(LogOutputGuard.encodedForms(canary)).contains(canary).allSatisfy(form ->
                assertThat(LogOutputGuard.leaks("{\"message\":\"x " + form + " y\"}", List.of(canary)))
                        .as("form %s", form).hasSize(1));
    }

    @Test
    @Proves("T-AUD-013")
    void textWithoutASecretIsClean() {
        assertThat(LogOutputGuard.leaks("{\"message\":\"Key loaded: fingerprint=67ffa38a\"}", CANARIES)).isEmpty();
    }

    @Test
    void aFindingNamesTheEncodingButNeverEchoesTheSecret() {
        List<String> findings = LogOutputGuard.leaks("prefix " + TestSecrets.ADMIN_PASSWORD, CANARIES);

        assertThat(findings).singleElement().asString().contains("plain text")
                .doesNotContain(TestSecrets.ADMIN_PASSWORD);
    }

    @Test
    @Proves("T-AUD-013")
    void aRegisteredServerSecretIsScannedForUntilTheTestEnds() {
        LogOutputGuard.register("server-secret-1f9e3a");
        System.out.println("session cookie: server-secret-1f9e3a");

        assertThat(LogOutputGuard.scanNow(false)).hasSize(1);
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }

    @Test
    void aSecretSplitAcrossTwoScansIsStillFound() {
        LogOutputGuard.register("server-secret-7c2d8b");
        System.out.print("start server-secr");
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
        System.out.println("et-7c2d8b end");

        assertThat(LogOutputGuard.scanNow(false)).hasSize(1);
    }

    @Test
    void aShortSecretIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> LogOutputGuard.register("short"));
    }

    @Test
    void aDegradedRowIsReportedUnlessExpected() {
        System.out.println("{\"message\":\"" + AuditEmitter.DEGRADED_MESSAGE + "\"}");
        assertThat(LogOutputGuard.scanNow(true)).isEmpty();

        System.out.println("{\"message\":\"" + AuditEmitter.DEGRADED_MESSAGE + "\"}");
        assertThat(LogOutputGuard.scanNow(false)).singleElement().asString().contains("degraded audit row");
    }
}
