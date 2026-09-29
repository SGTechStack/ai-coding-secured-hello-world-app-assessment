package com.eitri.auth;

import java.util.Arrays;
import java.util.Optional;

/** The fixed set of account roles. Spring Security sees each as the authority {@code ROLE_<name>}. */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Exactly {@code "USER"} or {@code "ADMIN"}; any other value (other case, retired roles) is empty. */
    public static Optional<Role> fromName(Object value) {
        return Arrays.stream(values()).filter(role -> role.name().equals(value)).findFirst();
    }
}
