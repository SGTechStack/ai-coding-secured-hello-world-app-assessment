package com.example.demo_app.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated request field must meet the {@link PasswordPolicy}; {@code null} fails. The
 * violation message is the policy's own, which never contains the password. Put it on every
 * request field that sets a password, so a policy failure is reported with the other field errors
 * in one {@code 400 VALIDATION_FAILED}.
 */
@Documented
@Constraint(validatedBy = AcceptablePasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface AcceptablePassword {

  /** Unused: the validator reports the policy's specific message instead. */
  String message() default "Password does not meet the password policy.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
