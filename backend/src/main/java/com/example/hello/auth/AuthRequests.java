package com.example.hello.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;

public final class AuthRequests {
    private AuthRequests() {}

    public record Register(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "Use 3–50 letters, numbers, or underscores.") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        public Register { username = normalize(username); email = normalize(email); }
        @Override public String toString() { return "Register[redacted]"; }
    }

    public record Login(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {
        public Login { username = normalize(username); }
        @Override public String toString() { return "Login[redacted]"; }
    }

    public static String normalize(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
}
