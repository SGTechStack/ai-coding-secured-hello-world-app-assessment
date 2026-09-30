package sg.securedhello.recovery;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What a run rebinds, always named explicitly (ADR-072): an admin can hold both states, and an implicit default would
 * be a half-recovery that looks complete.
 */
enum RecoveryScope {

    /** The password: set (single form) or invalidated (batch form), with the lockout and the NIST cap cleared. */
    PASSWORD,
    /** The TOTP factor: deleted, both lockout tiers with it, so the admin enrols again. */
    TOTP,
    /** Both. */
    BOTH;

    boolean password() {
        return this != TOTP;
    }

    boolean totp() {
        return this != PASSWORD;
    }

    /** The value on the command line and in the audit row. */
    String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<RecoveryScope> of(String code) {
        return Stream.of(values()).filter(scope -> scope.code().equals(code)).findFirst();
    }
}
