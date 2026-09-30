package local.builderday.account.passwordreset.controller.dto;

/** Body of {@code POST /api/auth/password-reset}. The email is validated by the service's account rules. */
public record PasswordResetRequest(String email) {}
