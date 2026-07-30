package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin cập nhật tài khoản người dùng")
public record UpdateUserRequest(
        @Schema(description = "Họ và tên mới", example = "Nguyen Van B", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Schema(description = "Vai trò người dùng", example = "INSTRUCTOR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Role is required")
        UserRole role,

        @Schema(description = "Trạng thái tài khoản mới", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Status is required")
        AccountStatus status,

        @Schema(description = "Mật khẩu mới (Tùy chọn, để trống nếu không đổi mật khẩu)", example = "NewPassword@123")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters if provided")
        String password) {}

