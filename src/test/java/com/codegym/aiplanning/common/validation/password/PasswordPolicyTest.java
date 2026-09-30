package com.codegym.aiplanning.common.validation.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @Test
    @DisplayName("Valid password satisfying all rules should have no violations")
    void validPassword_HasNoViolations() {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate("ValidPass123");
        assertThat(violations).isEmpty();
        assertThat(PasswordPolicy.isValid("ValidPass123")).isTrue();
        assertDoesNotThrow(() -> PasswordPolicy.validateOrThrow("ValidPass123"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Null or empty password returns PASSWORD_REQUIRED")
    void nullOrEmptyPassword_ReturnsRequiredViolation(String password) {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate(password);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(PasswordPolicy.CODE_REQUIRED);
    }

    @Test
    @DisplayName("Password with 7 characters returns PASSWORD_TOO_SHORT")
    void shortPassword_ReturnsTooShortViolation() {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate("Pass123");
        assertThat(violations).anyMatch(v -> PasswordPolicy.CODE_TOO_SHORT.equals(v.code()));
    }

    @Test
    @DisplayName("Password with exactly 8 characters is accepted for length")
    void boundary8Characters_Accepted() {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate("Pass1234");
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Password with exactly 50 characters is accepted for length")
    void boundary50Characters_Accepted() {
        String pass50 = "P1" + "a".repeat(48);
        List<PasswordRuleViolation> violations = PasswordPolicy.validate(pass50);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Password with 51 characters returns PASSWORD_TOO_LONG")
    void boundary51Characters_ReturnsTooLongViolation() {
        String pass51 = "P1" + "a".repeat(49);
        List<PasswordRuleViolation> violations = PasswordPolicy.validate(pass51);
        assertThat(violations).anyMatch(v -> PasswordPolicy.CODE_TOO_LONG.equals(v.code()));
    }

    @Test
    @DisplayName("Password missing uppercase returns PASSWORD_MISSING_UPPERCASE")
    void missingUppercase_ReturnsMissingUppercaseViolation() {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate("lowercase123");
        assertThat(violations).anyMatch(v -> PasswordPolicy.CODE_MISSING_UPPERCASE.equals(v.code()));
    }

    @Test
    @DisplayName("Password missing digit returns PASSWORD_MISSING_DIGIT")
    void missingDigit_ReturnsMissingDigitViolation() {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate("UppercaseOnly");
        assertThat(violations).anyMatch(v -> PasswordPolicy.CODE_MISSING_DIGIT.equals(v.code()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pass word123", " Password123", "Password123 ", "Pass\tword123", "Pass\nword123"})
    @DisplayName("Password containing whitespace returns PASSWORD_CONTAINS_WHITESPACE")
    void whitespaceInPassword_ReturnsWhitespaceViolation(String password) {
        List<PasswordRuleViolation> violations = PasswordPolicy.validate(password);
        assertThat(violations).anyMatch(v -> PasswordPolicy.CODE_CONTAINS_WHITESPACE.equals(v.code()));
    }

    @Test
    @DisplayName("validateOrThrow throws BusinessException with PASSWORD_POLICY_VIOLATION")
    void validateOrThrow_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () -> PasswordPolicy.validateOrThrow("short"));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
    }
}
