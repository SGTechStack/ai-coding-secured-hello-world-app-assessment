package sg.securedhello.admin;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The two roles an administrator can set (ADR-042), as the role-change request carries them. Each constant's name is
 * the value stored in {@code users.role}.
 *
 * <p>Bound from its exact name only: not case-folded, and never from an ordinal or an ordinal's string, so any other
 * value fails to bind and is the same 400 {@code VALIDATION_FAILED} as a missing role.
 */
public enum Role {

    /** The role a demotion leaves. */
    USER,

    /** The role a promotion grants. */
    ADMIN;

    /**
     * The role named exactly {@code name}.
     *
     * @throws IllegalArgumentException if no role has that name
     */
    @JsonCreator
    static Role named(String name) {
        return Arrays.stream(values()).filter(role -> role.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No role is named " + name));
    }
}
