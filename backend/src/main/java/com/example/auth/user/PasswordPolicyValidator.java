package com.example.auth.user;

import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Applies {@link PasswordPolicy#validate} as a Bean Validation constraint.
 * Each policy violation is added as a separate constraint-violation message
 * so clients can surface all errors at once rather than fix-and-retry.
 * The submitted password value is never included in any message.
 */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false; // @NotBlank already handles null/blank; delegate to it
        }

        List<String> violations = PasswordPolicy.validate(value);
        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        for (String message : violations) {
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        }
        return false;
    }
}
