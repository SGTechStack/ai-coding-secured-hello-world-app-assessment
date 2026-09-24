package com.example.demo_app.user;

import com.example.demo_app.web.ApiError.FieldError;
import com.example.demo_app.web.ApiException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Creates accounts. The single way in for self-registration and, later, the admin bootstrap, so
 * every account gets the same normalisation, password policy and BCrypt encoder:
 *
 * <ul>
 *   <li>the username and email are stored normalised (lowercase; the email also trimmed);
 *   <li>the password must meet the {@link PasswordPolicy}, else {@code 400 VALIDATION_FAILED}
 *       naming {@code password};
 *   <li>a username or email already in use, in any case, is {@code 409 ACCOUNT_CONFLICT} naming
 *       each conflicting field. This reveals that the account exists, as the PRD requires; the
 *       registration throttle limits how fast anyone can probe with it;
 *   <li>the account is enabled, has the role the caller passes, and {@code created_at} from the
 *       {@link Clock}.
 * </ul>
 */
@Service
public class AccountRegistration {

  static final String USERNAME_TAKEN = "This username is already taken.";
  static final String EMAIL_TAKEN = "An account with this email already exists.";

  private final UserAccountRepository accounts;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AccountRegistration(
      UserAccountRepository accounts,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.accounts = accounts;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * Creates the account with {@code role} and returns it. Self-registration always passes {@link
   * Role#USER}; nothing in a request can choose the role.
   *
   * @throws ApiException {@code 400 VALIDATION_FAILED} for a password the policy refuses, or
   *     {@code 409 ACCOUNT_CONFLICT} for a username or email already in use
   */
  public UserAccount register(NewAccount account, Role role) {
    passwordPolicy
        .problem(account.password())
        .ifPresent(
            problem -> {
              throw ApiException.validationFailed(List.of(new FieldError("password", problem)));
            });
    String username = UserAccount.normaliseUsername(account.username());
    String email = UserAccount.normaliseEmail(account.email());
    throwIfTaken(username, email);

    UserAccount created =
        new UserAccount(
            username,
            email,
            account.firstName(),
            passwordEncoder.encode(account.password()),
            role,
            clock.instant());
    try {
      return accounts.saveAndFlush(created);
    } catch (DataIntegrityViolationException raced) {
      // Another request took the username or email between the check and the insert.
      throwIfTaken(username, email);
      throw raced;
    }
  }

  private void throwIfTaken(String username, String email) {
    List<FieldError> conflicts = new ArrayList<>();
    if (accounts.existsByUsername(username)) {
      conflicts.add(new FieldError("username", USERNAME_TAKEN));
    }
    if (accounts.existsByEmail(email)) {
      conflicts.add(new FieldError("email", EMAIL_TAKEN));
    }
    if (!conflicts.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ACCOUNT_CONFLICT",
          "An account with these details already exists",
          conflicts);
    }
  }
}
