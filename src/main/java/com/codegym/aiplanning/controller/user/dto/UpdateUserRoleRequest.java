package com.codegym.aiplanning.controller.user.dto;

import com.codegym.aiplanning.entity.auth.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Vai trò không được để trống")
        UserRole role
) {}
