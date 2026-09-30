package sg.example.helloauth.password;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * The string meets the {@link PasswordPolicy}. Each broken rule is reported as its own error on
 * the field, so a validation failure lists all of them at once.
 */
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {

    String message() default "does not meet the password policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {

        private final PasswordPolicy policy;

        public Validator(PasswordPolicy policy) {
            this.policy = policy;
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            List<String> violations = policy.violations(value);
            if (violations.isEmpty()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            violations.forEach(violation -> context.buildConstraintViolationWithTemplate(violation)
                    .addConstraintViolation());
            return false;
        }
    }
}
