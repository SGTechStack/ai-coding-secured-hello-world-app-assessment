package local.builderday.account.passwordreset.service;

import java.util.List;

/** What completing a Password reset did. */
public sealed interface ConfirmResult {
  /** The password was changed and every Session of the account ended. */
  record Reset() implements ConfirmResult {}

  /**
   * The token was malformed, unknown, expired, used, or its account is no longer eligible; the client never learns
   * which.
   */
  record InvalidToken() implements ConfirmResult {}

  /**
   * The new password was refused; the token was not spent. @param codes stable violation codes for
   * {@code newPassword}
   */
  record Rejected(List<String> codes) implements ConfirmResult {}

  /** The source IP is over its confirm rate limit; the token was not looked up. */
  record RateLimited() implements ConfirmResult {}
}
