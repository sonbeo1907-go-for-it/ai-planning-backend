package com.codegym.aiplanning.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required")
        String token,
        @NotBlank(message = "New password is required")
        String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest{" +
                "token='[PROTECTED]'" +
                ", newPassword='[PROTECTED]'" +
                '}';
    }
}
