package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * Every serialised reason code equals this committed list (ADR-055 constraint 5). The codes are saved-query targets:
 * renaming a constant, or changing what {@code code()} returns, must fail here rather than silently break a
 * dashboard. A new reason family adds its codes to the list in the same change.
 */
class AuditReasonCodesTest {

    /** The committed list, family by family. */
    private static final List<String> PINNED_CODES = List.of(
            // Degradation: the degraded row's reasons
            "UNKNOWN_KEY", "MISSING_KEY", "REASON_OUTSIDE_FAMILY", "EMIT_FAILED",
            // LoginFailureReason: row 2
            "BAD_CREDENTIALS", "UNKNOWN_USER", "ACCOUNT_LOCKED", "ACCOUNT_DISABLED", "CREDENTIAL_EXPIRED",
            // SessionStartReason: row 8
            "LOGIN",
            // CsrfReason: row 13
            "CSRF_MISSING", "CSRF_INVALID");

    @Test
    @Proves("T-AUD-045")
    void theSerialisedReasonCodesEqualTheCommittedList() {
        assertThat(serialisedCodes()).containsExactlyInAnyOrderElementsOf(PINNED_CODES).doesNotHaveDuplicates();
    }

    @Test
    @Proves("T-AUD-045")
    void everyReasonFamilyIsAnEnumSoItsCodesCanBeListed() {
        assertThat(AuditReason.class.getPermittedSubclasses()).allSatisfy(family -> assertThat(family.isEnum())
                .as("%s is an enum", family).isTrue());
    }

    private static List<String> serialisedCodes() {
        return Arrays.stream(AuditReason.class.getPermittedSubclasses())
                .flatMap(family -> Stream.of((AuditReason[]) family.getEnumConstants()))
                .map(AuditReason::code)
                .toList();
    }
}
