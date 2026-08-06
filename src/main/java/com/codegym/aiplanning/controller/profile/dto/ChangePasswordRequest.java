package com.codegym.aiplanning.controller.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body cho tính năng đổi mật khẩu (AUTH-08)")
public record ChangePasswordRequest(
        @Schema(description = "Mật khẩu hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "Mật khẩu mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 50, message = "New password must be between 6 and 50 characters")
        String newPassword,

        @Schema(description = "Xác nhận mật khẩu mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest{" +
                "currentPassword='[PROTECTED]'" +
                ", newPassword='[PROTECTED]'" +
                ", confirmPassword='[PROTECTED]'" +
                '}';
    }
}
