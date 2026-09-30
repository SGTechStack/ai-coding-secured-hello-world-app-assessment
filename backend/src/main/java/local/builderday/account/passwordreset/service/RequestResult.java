package local.builderday.account.passwordreset.service;

import java.util.List;
import local.builderday.account.core.model.AccountRuleViolation;

/** What a reset request got. {@link Accepted} never says whether an email was sent. */
public sealed interface RequestResult {
  record Accepted() implements RequestResult {}

  /** The email broke its rules. */
  record Invalid(List<AccountRuleViolation> violations) implements RequestResult {}

  /** The source IP is over its request rate limit. */
  record RateLimited() implements RequestResult {}
}
