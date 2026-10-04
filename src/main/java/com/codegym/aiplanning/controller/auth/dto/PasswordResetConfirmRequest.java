package com.codegym.aiplanning.controller.auth.dto;

import com.codegym.aiplanning.common.validation.password.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Password reset confirmation request")
public record PasswordResetConfirmRequest(
        @Schema(description = "Reset token received via email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Token is required")
        String token,

        @Schema(
                description = "New password: 8-50 characters with at least one uppercase letter and one digit, no whitespace",
                example = "NewPassword123",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = 8,
                maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidPassword
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
