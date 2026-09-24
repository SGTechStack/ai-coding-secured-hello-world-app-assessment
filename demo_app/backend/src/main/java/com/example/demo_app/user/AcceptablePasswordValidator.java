package com.example.demo_app.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

/**
 * Checks {@link AcceptablePassword} with the {@link PasswordPolicy} bean, which Spring injects
 * (its validator factory builds constraint validators as beans).
 */
class AcceptablePasswordValidator implements ConstraintValidator<AcceptablePassword, String> {

  private final PasswordPolicy policy;

  AcceptablePasswordValidator(PasswordPolicy policy) {
    this.policy = policy;
  }

  @Override
  public boolean isValid(String password, ConstraintValidatorContext context) {
    Optional<String> problem = policy.problem(password);
    if (problem.isEmpty()) {
      return true;
    }
    // The policy's messages are fixed text without {} or $, so interpolation leaves them as is.
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(problem.get()).addConstraintViolation();
    return false;
  }
}
