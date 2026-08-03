package com.codegym.aiplanning.controller.admin.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminResetPasswordRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenPasswordIsValid_thenNoViolations() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("validPassword123");
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenPasswordIsTooShort_thenViolation() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("short");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertEquals("Password must be between 6 and 50 characters", violations.iterator().next().getMessage());
    }

    @Test
    void whenPasswordIsBlank_thenViolation() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void toString_shouldHidePassword() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("mySecretPassword");
        String result = request.toString();
        assertTrue(result.contains("[PROTECTED]"));
        assertFalse(result.contains("mySecretPassword"));
    }
}
