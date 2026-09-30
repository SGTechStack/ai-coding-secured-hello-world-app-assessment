package local.builderday.account.core.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import local.builderday.account.core.model.AccountRuleViolation;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The full password policy for a new password: the composition rules plus a local list of the 10,000 most common
 * passwords (SecLists {@code 10k-most-common.txt}, MIT licence). Backend-only: the list is never shipped to the
 * frontend, and no third-party service is called.
 */
@Component
public class PasswordPolicy {
  private static final String COMMON_PASSWORDS = "security/common-passwords.txt";
  /** Lowercased, so trivially capitalised variants of a listed password are also rejected. */
  private final Set<String> commonPasswords;

  PasswordPolicy() {
    try {
      commonPasswords = new ClassPathResource(COMMON_PASSWORDS).getContentAsString(StandardCharsets.UTF_8).lines()
          .filter(line -> !line.isEmpty())
          .map(line -> line.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    } catch (IOException exception) {
      throw new UncheckedIOException("Common-password list is unavailable.", exception);
    }
  }

  /**
   * The password is inspected exactly as submitted: it is never trimmed or normalized.
   *
   * @param username normalized username of the account, possibly null
   * @param email normalized email of the account, possibly null
   */
  public List<AccountRuleViolation> violations(String password, String username, String email) {
    var violations = new ArrayList<>(AccountRules.passwordViolations(password, username, email));
    if (password != null && !password.isEmpty() && commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
      violations.add(AccountRuleViolation.PASSWORD_TOO_COMMON);
    }
    return violations;
  }
}
