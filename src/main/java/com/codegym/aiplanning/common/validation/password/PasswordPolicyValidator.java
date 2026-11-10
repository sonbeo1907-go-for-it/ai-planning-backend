package com.codegym.aiplanning.common.validation.password;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    public void validate(String password) {
        PasswordPolicy.validateOrThrow(password);
    }
}
