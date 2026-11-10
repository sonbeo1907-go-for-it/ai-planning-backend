package com.codegym.aiplanning.common.validation.password;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Single Source of Truth (SSOT) for application password policy.
 * Unified rules:
 * - 8 to 50 characters
 * - At least one uppercase letter (A-Z)
 * - At least one digit (0-9)
 * - No whitespace characters allowed
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 50;

    public static final String CODE_REQUIRED = "PASSWORD_REQUIRED";
    public static final String CODE_TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String CODE_TOO_LONG = "PASSWORD_TOO_LONG";
    public static final String CODE_MISSING_UPPERCASE = "PASSWORD_MISSING_UPPERCASE";
    public static final String CODE_MISSING_DIGIT = "PASSWORD_MISSING_DIGIT";
    public static final String CODE_CONTAINS_WHITESPACE = "PASSWORD_CONTAINS_WHITESPACE";

    public static final String MSG_REQUIRED = "Mật khẩu không được để trống.";
    public static final String MSG_TOO_SHORT = "Mật khẩu phải có tối thiểu 8 ký tự.";
    public static final String MSG_TOO_LONG = "Mật khẩu không được vượt quá 50 ký tự.";
    public static final String MSG_MISSING_UPPERCASE = "Mật khẩu phải chứa ít nhất 1 chữ hoa.";
    public static final String MSG_MISSING_DIGIT = "Mật khẩu phải chứa ít nhất 1 chữ số.";
    public static final String MSG_CONTAINS_WHITESPACE = "Mật khẩu không được chứa khoảng trắng.";

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile(".*\\s.*");

    private PasswordPolicy() {}

    public static List<PasswordRuleViolation> validate(String password) {
        if (password == null || password.isEmpty()) {
            return List.of(new PasswordRuleViolation(CODE_REQUIRED, MSG_REQUIRED));
        }

        List<PasswordRuleViolation> violations = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            violations.add(new PasswordRuleViolation(CODE_TOO_SHORT, MSG_TOO_SHORT));
        } else if (password.length() > MAX_LENGTH) {
            violations.add(new PasswordRuleViolation(CODE_TOO_LONG, MSG_TOO_LONG));
        }

        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            violations.add(new PasswordRuleViolation(CODE_MISSING_UPPERCASE, MSG_MISSING_UPPERCASE));
        }

        if (!DIGIT_PATTERN.matcher(password).matches()) {
            violations.add(new PasswordRuleViolation(CODE_MISSING_DIGIT, MSG_MISSING_DIGIT));
        }

        if (WHITESPACE_PATTERN.matcher(password).matches()) {
            violations.add(new PasswordRuleViolation(CODE_CONTAINS_WHITESPACE, MSG_CONTAINS_WHITESPACE));
        }

        return Collections.unmodifiableList(violations);
    }

    public static void validateOrThrow(String password) {
        List<PasswordRuleViolation> violations = validate(password);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, violations.get(0).message());
        }
    }

    public static boolean isValid(String password) {
        return validate(password).isEmpty();
    }

    public static boolean isPasswordPolicyCode(String code) {
        return CODE_REQUIRED.equals(code)
                || CODE_TOO_SHORT.equals(code)
                || CODE_TOO_LONG.equals(code)
                || CODE_MISSING_UPPERCASE.equals(code)
                || CODE_MISSING_DIGIT.equals(code)
                || CODE_CONTAINS_WHITESPACE.equals(code);
    }

    public static String resolveMessage(String code) {
        return switch (code) {
            case CODE_REQUIRED -> MSG_REQUIRED;
            case CODE_TOO_SHORT -> MSG_TOO_SHORT;
            case CODE_TOO_LONG -> MSG_TOO_LONG;
            case CODE_MISSING_UPPERCASE -> MSG_MISSING_UPPERCASE;
            case CODE_MISSING_DIGIT -> MSG_MISSING_DIGIT;
            case CODE_CONTAINS_WHITESPACE -> MSG_CONTAINS_WHITESPACE;
            default -> "Mật khẩu không hợp lệ.";
        };
    }
}
