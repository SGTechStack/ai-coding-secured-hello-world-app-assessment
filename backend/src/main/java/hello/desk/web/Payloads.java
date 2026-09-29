package hello.desk.web;

import hello.desk.security.PasswordPolicy;
import hello.desk.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class Payloads {

    private Payloads() {
    }

    record RegisterRequest(
            @NotBlank(message = "Username is required.")
            @Pattern(
                    regexp = "^[A-Za-z0-9._-]{3,50}$",
                    message = "Username must be 3-50 characters and use letters, numbers, dots, underscores, or hyphens.")
            String username,
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            String email,
            @NotBlank(message = "Password is required.")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = ApiMessages.WEAK_PASSWORD)
            String password) {
    }

    record LoginRequest(
            @NotBlank(message = "Username is required.") String username,
            @NotBlank(message = "Password is required.") String password) {
    }

    record ResetRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            String email) {
    }

    record ResetConfirmRequest(
            @NotBlank(message = "Reset token is required.") String token,
            @NotBlank(message = "Password is required.")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = ApiMessages.WEAK_PASSWORD)
            String password) {
    }

    record EnabledRequest(@NotNull(message = "Enabled flag is required.") Boolean enabled) {
    }

    record RoleRequest(@NotNull(message = "Role is required.") Role role) {
    }

    record ErrorBody(String message) {
    }

    record MessageBody(String message) {
    }

    record GreetingBody(String message) {
    }

    record CsrfBody(String token, String headerName) {
    }
}
