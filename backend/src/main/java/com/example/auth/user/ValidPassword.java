package com.example.auth.user;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint that enforces the application's {@link PasswordPolicy}.
 * Apply to any {@code String} field that holds a new or replacement password.
 * The annotation is intentionally placed at the field/parameter level so that
 * Bean Validation runs automatically when {@code @Valid} is present on a controller
 * argument — the policy is enforced even if the frontend is bypassed.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password does not meet the required policy.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
