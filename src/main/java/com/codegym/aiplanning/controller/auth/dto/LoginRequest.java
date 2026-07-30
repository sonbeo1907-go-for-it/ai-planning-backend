package com.codegym.aiplanning.controller.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin đăng nhập người dùng")
public record LoginRequest(
        @Schema(description = "Mã/Tên đăng nhập", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String username,

        @Schema(description = "Mật khẩu đăng nhập", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String password) {}

