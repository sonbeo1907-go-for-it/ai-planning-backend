package com.codegym.aiplanning.common.validation.password;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Test
    void validate_WithValidPassword_DoesNotThrowException() {
        assertDoesNotThrow(() -> validator.validate("StrongPass123!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void validate_WithBlankPassword_ThrowsException(String blankPassword) {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(blankPassword));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Password must not be blank.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Short1!", "LongPassWithoutNumbers!"}) // We will test specific violations below
    void validate_WithTooShortPassword_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("Sh1!"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Mật khẩu phải từ 8 đến 50 ký tự.", exception.getMessage());
    }

    @Test
    void validate_WithoutUppercase_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("weakpass123!"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ hoa.", exception.getMessage());
    }

    @Test
    void validate_WithoutLowercase_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("WEAKPASS123!"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ thường.", exception.getMessage());
    }

    @Test
    void validate_WithoutNumber_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("WeakPassword!"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Mật khẩu phải chứa ít nhất 1 chữ số.", exception.getMessage());
    }

    @Test
    void validate_WithoutSpecialCharacter_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate("WeakPassword123"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATION, exception.errorCode());
        assertEquals("Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt.", exception.getMessage());
    }
}
