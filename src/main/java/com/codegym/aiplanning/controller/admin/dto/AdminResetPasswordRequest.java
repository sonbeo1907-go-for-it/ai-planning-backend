package com.codegym.aiplanning.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, max = 50, message = "Password must be between 6 and 50 characters")
        String newPassword
) {
    @Override
    public String toString() {
        return "AdminResetPasswordRequest{" +
                "newPassword='[PROTECTED]'" +
                '}';
    }
}
