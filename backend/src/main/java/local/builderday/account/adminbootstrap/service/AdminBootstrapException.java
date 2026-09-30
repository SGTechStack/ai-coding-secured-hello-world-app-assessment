package local.builderday.account.adminbootstrap.service;

import java.util.List;
import local.builderday.account.core.model.AccountRuleViolation;

/**
 * Fails startup when Admin bootstrap cannot safely produce the Admin the operator asked for (ADR 0009 §4). Thrown from
 * the {@code ApplicationRunner}, so Spring Boot's startup failure reporting shows it. Its message names the reason
 * code and, for a policy failure, the {@link AccountRuleViolation} codes, and never the submitted username or password
 * (Story 6, IM8 as-8).
 */
public class AdminBootstrapException extends RuntimeException {
  private final String reason;

  AdminBootstrapException(String reason, String detail) {
    super("Admin bootstrap failed (" + reason + "): " + detail);
    this.reason = reason;
  }

  /** The stable failure reason code, e.g. {@code username-invalid}, {@code password-policy}, {@code username-taken}. */
  public String reason() {
    return reason;
  }

  static AdminBootstrapException of(String reason, String detail) {
    return new AdminBootstrapException(reason, detail);
  }

  /** A validation failure carrying only the violation codes, never the submitted value. */
  static AdminBootstrapException ofViolations(String reason, List<AccountRuleViolation> violations) {
    return new AdminBootstrapException(reason,
        violations.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
  }
}
