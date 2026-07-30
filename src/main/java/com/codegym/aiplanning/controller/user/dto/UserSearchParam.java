package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tham số tìm kiếm và phân trang người dùng")
public record UserSearchParam(
        @Schema(description = "Từ khóa tìm kiếm theo username hoặc họ tên", example = "student")
        String search,

        @Schema(description = "Lọc theo vai trò (STUDENT, INSTRUCTOR, ADMIN)", example = "STUDENT")
        UserRole role,

        @Schema(description = "Lọc theo trạng thái (ACTIVE, INACTIVE, LOCKED)", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "Số trang (bắt đầu từ 0)", example = "0", defaultValue = "0")
        Integer page,

        @Schema(description = "Số lượng bản ghi mỗi trang (tối đa 100)", example = "10", defaultValue = "10")
        Integer size) {

    public int resolvedPage() {
        return page != null && page >= 0 ? page : 0;
    }

    public int resolvedSize() {
        return size != null && size > 0 && size <= 100 ? size : 10;
    }
}

