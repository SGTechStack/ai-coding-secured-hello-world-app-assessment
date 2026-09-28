package com.example.helloauth.domain;

/**
 * The single authority held by an account. There is exactly one role per account and no finer
 * grain: per-resource authorization beyond this check is out of scope per the PRD.
 */
public enum Role {
    USER,
    ADMIN;

    /** The authority name Spring Security expects, i.e. the role with its conventional prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
