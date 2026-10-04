package com.codegym.aiplanning.common.validation.password;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Test
    void validate_WithValidPassword_DoesNotThrowException() {
        assertDoesNotThrow(() -> validator.validate("StrongPass123"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validate_WithBlankPassword_ThrowsException(String blankPassword) {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(blankPassword));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals(PasswordPolicy.MSG_REQUIRED, exception.getMessage());
    }

    @Test
    void validate_WithTooShortPassword_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("Sh1!"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals(PasswordPolicy.MSG_TOO_SHORT, exception.getMessage());
    }

    @Test
    void validate_WithoutUppercase_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("weakpass123"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals(PasswordPolicy.MSG_MISSING_UPPERCASE, exception.getMessage());
    }

    @Test
    void validate_WithoutNumber_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("WeakPassword"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals(PasswordPolicy.MSG_MISSING_DIGIT, exception.getMessage());
    }

    @Test
    void validate_WithWhitespace_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("Weak Pass123"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals(PasswordPolicy.MSG_CONTAINS_WHITESPACE, exception.getMessage());
    }
}
