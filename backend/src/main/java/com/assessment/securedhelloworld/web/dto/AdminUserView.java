package com.assessment.securedhelloworld.web.dto;

import com.assessment.securedhelloworld.domain.Role;

import java.time.Instant;

/**
 * Admin-facing projection of {@code User}. Deliberately excludes {@code passwordHash} and any
 * other credential material — this is the only shape {@code GET /api/admin/users} ever returns
 * (PRD Story 8).
 */
public record AdminUserView(
        Long id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant lastLoginAt
) {
}
