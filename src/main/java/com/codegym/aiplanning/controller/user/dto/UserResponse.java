package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin tài khoản người dùng chi tiết")
public record UserResponse(
        @Schema(description = "ID định danh UUID của người dùng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
        UUID id,

        @Schema(description = "Mã người dùng nội bộ/hiển thị", example = "student_01")
        String username,

        @Schema(description = "Email đăng nhập", example = "student01@example.com")
        String email,

        @Schema(description = "Họ và tên người dùng", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "Vai trò người dùng", example = "STUDENT")
        UserRole role,

        @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "Thời gian khởi tạo tài khoản")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật gần nhất")
        Instant updatedAt,

        @Schema(description = "Lý do vô hiệu hóa (hiển thị khi tài khoản INACTIVE)")
        DeactivationReasonCode deactivationReasonCode) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getStatus() == AccountStatus.INACTIVE ? account.getDeactivationReasonCode() : null);
    }
}
