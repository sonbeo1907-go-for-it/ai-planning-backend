package com.codegym.aiplanning.controller.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Thông tin cá nhân người dùng đang đăng nhập")
public record ProfileResponse(
        @Schema(description = "ID định danh người dùng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
        UUID id,

        @Schema(description = "Mã/Tên đăng nhập", example = "admin")
        String username,

        @Schema(description = "Họ và tên", example = "Administrator")
        String fullName,

        @Schema(description = "Danh sách vai trò", example = "[\"ROLE_ADMIN\"]")
        List<String> roles) {}

