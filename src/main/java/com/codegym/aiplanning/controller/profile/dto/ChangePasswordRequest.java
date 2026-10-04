package com.codegym.aiplanning.controller.profile.dto;

import com.codegym.aiplanning.common.validation.password.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body cho tính năng đổi mật khẩu (AUTH-08)")
public record ChangePasswordRequest(
        @Schema(description = "Mật khẩu hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(
                description = "Mật khẩu mới: 8-50 ký tự, ít nhất 1 chữ hoa, 1 chữ số, không khoảng trắng",
                example = "NewPassword123",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = 8,
                maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidPassword
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
