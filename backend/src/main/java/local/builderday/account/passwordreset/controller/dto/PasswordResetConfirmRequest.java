package local.builderday.account.passwordreset.controller.dto;

/** Body of {@code POST /api/auth/password-reset/confirm}: the token from the link's fragment and the new password. */
public record PasswordResetConfirmRequest(String token, String newPassword) {}
