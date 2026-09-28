package sg.securedhello.audit;

import java.util.Optional;
import java.util.Set;

/**
 * The emitter's key validation (ADR-055 constraints 2 and 5), a pure decision: may these fields be written as this
 * row? Unknown keys are rejected, not only missing ones, because the allowed-key list is what keeps a call site from
 * adding {@code user.email} to a row. It is in the mutation-testing scope.
 */
final class EmitterKeyValidator {

    private EmitterKeyValidator() {
    }

    /**
     * Why {@code fields} cannot be written as {@code row}, or empty if they can.
     *
     * @param requestBound whether the current thread is serving a request, which a request-scoped row needs
     */
    static Optional<Degradation> check(AuditRowDefinition row, AuditFields fields, boolean requestBound) {
        Set<AuditKey> keys = fields.values().keySet();
        if (!row.allowed().containsAll(keys)) {
            return Optional.of(Degradation.UNKNOWN_KEY);
        }
        if (!keys.containsAll(row.required())) {
            return Optional.of(Degradation.MISSING_KEY);
        }
        if (row.scope() == AuditRowDefinition.Scope.REQUEST && !requestBound) {
            return Optional.of(Degradation.MISSING_KEY);
        }
        if (!reasonInFamily(row.reasonFamily(), fields.reason())) {
            return Optional.of(Degradation.REASON_OUTSIDE_FAMILY);
        }
        return Optional.empty();
    }

    /** A row without a family takes no reason; a row with one needs a reason of exactly that family. */
    private static boolean reasonInFamily(Class<? extends AuditReason> family, AuditReason reason) {
        if (family == AuditReason.None.class) {
            return reason == null;
        }
        return family.isInstance(reason);
    }
}
