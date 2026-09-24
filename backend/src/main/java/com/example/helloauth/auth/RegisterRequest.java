package com.example.helloauth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register} body — ratified shape {username, email,
 * password}. The {@code @Size} ceilings (security-review F-05) cap the
 * anonymous endpoint's cost surface: an unbounded password is a BCrypt
 * hashing amplifier (and BCrypt silently truncates past 72 bytes anyway),
 * while the email bound matches the RFC 5321 path maximum.
 */
public record RegisterRequest(
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 128) String password) {
}
