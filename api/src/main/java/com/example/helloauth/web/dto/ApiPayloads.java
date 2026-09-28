package com.example.helloauth.web.dto;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Every request and response body the API speaks, in one file.
 *
 * <p>They are gathered here rather than scattered as one-record-per-file because the whole HTTP
 * contract then fits on a screen and a half, which makes it possible to check at a glance that no
 * response carries a password hash.
 *
 * <p>Password length is <em>not</em> validated by annotation. The rule is 12–72 and lives in
 * configuration, applied by {@code PasswordPolicyValidator}, so that registration, password reset
 * and the admin seed cannot drift apart.
 */
public final class ApiPayloads {

    private ApiPayloads() {}

    /**
     * @param headerName the header the client should echo the token in, so the client does not have
     *     to hard-code Spring's convention
     */
    public record CsrfTokenResponse(String headerName, String token) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordResetConfirmRequest(
            @NotBlank String token, @NotBlank String password) {}

    /**
     * The answer to "is anyone logged in, and who?".
     *
     * <p>Returns 200 with {@code authenticated: false} for a visitor rather than 401, because the
     * frontend asks this on every page load and a 401 is not an error condition there.
     */
    public record SessionResponse(boolean authenticated, String username, Role role) {

        public static SessionResponse anonymous() {
            return new SessionResponse(false, null, null);
        }

        public static SessionResponse of(String username, Role role) {
            return new SessionResponse(true, username, role);
        }
    }

    public record MessageResponse(String message) {}

    /** What an admin sees about an account. The password hash is absent by construction. */
    public record AccountSummary(
            UUID id,
            String username,
            String email,
            Role role,
            boolean enabled,
            boolean locked,
            Instant createdAt) {

        public static AccountSummary from(Account account, Instant now) {
            return new AccountSummary(
                    account.getId(),
                    account.getUsername(),
                    account.getEmail(),
                    account.getRole(),
                    account.isEnabled(),
                    account.isLocked(now),
                    account.getCreatedAt());
        }
    }

    public record UpdateStatusRequest(@NotNull Boolean enabled) {}

    public record UpdateRoleRequest(@NotNull Role role) {}

    /**
     * The single error shape for every failure.
     *
     * @param code a stable machine-readable identifier, so the frontend branches on this and never
     *     on the human-readable text
     * @param fieldErrors present only for validation failures
     */
    public record ApiError(String code, String message, Map<String, String> fieldErrors) {

        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }
    }
}
