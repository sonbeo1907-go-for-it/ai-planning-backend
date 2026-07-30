package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin tạo tài khoản người dùng mới (US-ADM-01)")
public record CreateUserRequest(
        @Schema(description = "Mã/Tên đăng nhập (Duy nhất)", example = "student_01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @Schema(description = "Mật khẩu tài khoản (tối thiểu 6 ký tự)", example = "Password@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @Schema(description = "Họ và tên người dùng", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Schema(description = "Vai trò trong hệ thống", example = "STUDENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Role is required")
        UserRole role,

        @Schema(description = "Trạng thái tài khoản (Mặc định: ACTIVE)", example = "ACTIVE")
        AccountStatus status) {

    public AccountStatus resolvedStatus() {
        return status != null ? status : AccountStatus.ACTIVE;
    }
}

