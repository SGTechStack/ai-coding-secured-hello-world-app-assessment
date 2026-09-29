package sg.securedhello.error;

/**
 * The {@code rule} a {@code VALIDATION_FAILED} envelope may carry, when a validation failure is a property of the
 * submitted value the caller can act on. A plain format or length rejection carries none.
 */
public enum ValidationRule {

    /** Self-registration: the username is held by another account or a tombstone (ADR-032; ADR-044). */
    USERNAME_UNAVAILABLE
}
