package com.codegym.aiplanning.common.validation.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(PasswordPolicy.CODE_REQUIRED)
                    .addConstraintViolation();
            return false;
        }

        List<PasswordRuleViolation> violations = PasswordPolicy.validate(value);
        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        for (PasswordRuleViolation violation : violations) {
            context.buildConstraintViolationWithTemplate(violation.code())
                    .addConstraintViolation();
        }
        return false;
    }
}
