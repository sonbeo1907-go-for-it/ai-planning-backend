package com.codegym.aiplanning.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid IANA time-zone identifier recognized by the Java runtime.
 * Null or blank strings are considered valid by this constraint; use {@link jakarta.validation.constraints.NotBlank}
 * or {@link jakarta.validation.constraints.NotNull} to enforce presence.
 */
@Documented
@Constraint(validatedBy = IanaTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    String message() default "TIMEZONE_INVALID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
