package com.sgtechstack.helloauth.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint for {@link PasswordPolicy}.
 */
@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default PasswordPolicy.DESCRIPTION;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidPassword, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return PasswordPolicy.isAcceptable(value);
		}

	}

}
