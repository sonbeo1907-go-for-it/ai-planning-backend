package com.codegym.aiplanning.common.validation.password;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Password must not be blank.");
        }
        
        if (password.length() < 8 || password.length() > 50) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Mật khẩu phải từ 8 đến 50 ký tự.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Mật khẩu phải chứa ít nhất 1 chữ hoa.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Mật khẩu phải chứa ít nhất 1 chữ thường.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Mật khẩu phải chứa ít nhất 1 chữ số.");
        }
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt.");
        }
    }
}
