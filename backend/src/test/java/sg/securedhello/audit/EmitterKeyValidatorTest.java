package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static sg.securedhello.audit.AuditKey.ACTIVE_PROFILES;
import static sg.securedhello.audit.AuditKey.HOST_IP;
import static sg.securedhello.audit.AuditKey.HOST_NAME;
import static sg.securedhello.audit.AuditRowDefinition.row;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import sg.securedhello.audit.AuditRowDefinition.Outcome;
import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.audit.AuditRowDefinition.Severity;
import sg.securedhello.testsupport.Proves;

/** The emitter's key validation as a pure decision (ADR-055 constraints 2 and 5); in the mutation-testing scope. */
class EmitterKeyValidatorTest {

    /** Requires host.name, allows host.ip, takes no reason, not request-scoped. */
    private static final AuditRowDefinition PROCESS_ROW = row("test-row", "Test row.")
            .scope(Scope.PROCESS).required(HOST_NAME).optional(HOST_IP).build();

    private static final AuditRowDefinition REQUEST_ROW = row("test-row", "Test row.").build();

    private static final AuditRowDefinition REASONED_ROW = row("test-row", "Test row.")
            .scope(Scope.PROCESS).reasons(Degradation.class).build();

    @Test
    @Proves("T-AUD-043")
    void aKeyOutsideTheAllowedSetIsRejected() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_NAME, "h").put(ACTIVE_PROFILES, "dev"), false))
                .contains(Degradation.UNKNOWN_KEY);
    }

    @Test
    @Proves("T-AUD-043")
    void anUnknownKeyIsReportedEvenWhenARequiredKeyIsAlsoMissing() {
        assertThat(check(PROCESS_ROW, fields().put(ACTIVE_PROFILES, "dev"), false))
                .contains(Degradation.UNKNOWN_KEY);
    }

    @Test
    void theRequiredAndAllowedKeysPass() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_NAME, "h").put(HOST_IP, List.of("10.0.0.1")), false))
                .isEmpty();
    }

    @Test
    void anOptionalKeyMayBeLeftOut() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_NAME, "h"), false)).isEmpty();
    }

    @Test
    void aMissingRequiredKeyIsRejected() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_IP, List.of("10.0.0.1")), false))
                .contains(Degradation.MISSING_KEY);
    }

    @Test
    void aRequestScopedRowNeedsARequest() {
        assertThat(check(REQUEST_ROW, fields(), false)).contains(Degradation.MISSING_KEY);
        assertThat(check(REQUEST_ROW, fields(), true)).isEmpty();
    }

    @Test
    void aProcessRowIsWritableInsideARequestToo() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_NAME, "h"), true)).isEmpty();
    }

    @Test
    void aRowWithoutAReasonFamilyTakesNoReason() {
        assertThat(check(PROCESS_ROW, fields().put(HOST_NAME, "h").reason(Degradation.EMIT_FAILED), false))
                .contains(Degradation.REASON_OUTSIDE_FAMILY);
    }

    @Test
    void aRowWithAReasonFamilyNeedsAReasonOfThatFamily() {
        assertThat(check(REASONED_ROW, fields(), false)).contains(Degradation.REASON_OUTSIDE_FAMILY);
        assertThat(check(REASONED_ROW, fields().reason(Degradation.MISSING_KEY), false)).isEmpty();
    }

    @Test
    void aDefinitionCannotRequireAKeyItDoesNotAllow() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuditRowDefinition("a", List.of("info"),
                Outcome.SUCCESS, Level.INFO, Severity.LOW, "m", Scope.PROCESS, AuditReason.None.class,
                Set.of(HOST_NAME), Set.of()));
    }

    private static Optional<Degradation> check(AuditRowDefinition row, AuditFields fields, boolean requestBound) {
        return EmitterKeyValidator.check(row, fields, requestBound);
    }

    private static AuditFields fields() {
        return new AuditFields();
    }
}
