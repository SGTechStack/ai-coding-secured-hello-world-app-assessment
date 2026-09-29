package sg.securedhello.audit;

/**
 * An audit row's reason: {@code event.reason}. Each {@link AuditEvent} is bound to one family, an enum implementing
 * this interface, and the context record that carries the reason declares that family's type, so a reason from the
 * wrong family does not compile (ADR-055).
 *
 * <p>A reason is serialised by {@link #code()}, never by {@code name()}: the codes are saved-query targets, and
 * renaming a constant must not change them. T-AUD-045 pins every code.
 *
 * <p>A later ticket adds a family by writing an enum that implements this interface and naming it in
 * {@code permits}.
 */
public sealed interface AuditReason permits AuditReason.None, Degradation, LoginFailureReason,
        SessionStartReason, CsrfReason, SourceThrottleReason, IdentifierThrottleReason, TruncationReason,
        LockoutReason, LockoutClearReason, PasswordDisableReason {

    /** The pinned value written to {@code event.reason}. */
    String code();

    /** The family of an event that carries no reason. It has no constants, so no reason can be supplied. */
    enum None implements AuditReason {
        ;

        @Override
        public String code() {
            throw new UnsupportedOperationException("None has no constants");
        }
    }
}
