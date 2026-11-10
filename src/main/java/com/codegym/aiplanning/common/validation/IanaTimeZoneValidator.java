package com.codegym.aiplanning.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;

/**
 * Validates that a non-null, non-blank string matches an available IANA zone ID from the Java runtime.
 * Null and blank values are left to @NotNull / @NotBlank.
 */
public class IanaTimeZoneValidator implements ConstraintValidator<IanaTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        Set<String> availableZoneIds = ZoneId.getAvailableZoneIds();
        return availableZoneIds.contains(value);
    }
}
