package com.example.securedhello.entity;

/**
 * Account role. A newly registered account is always {@link #USER}; {@link
 * #ADMIN} is granted only by seeding or by an existing admin.
 */
public enum Role {
    USER,
    ADMIN
}
