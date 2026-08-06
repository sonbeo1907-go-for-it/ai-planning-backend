package com.codegym.aiplanning.controller.admin.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateClassRequest(
        @NotBlank(message = "Tên lớp học không được để trống")
        @Size(max = 255, message = "Tên lớp học không vượt quá 255 ký tự")
        String name,

        String description,

        Instant openedAt,
        Instant closedAt,

        @NotNull(message = "Version không được để trống")
        Long version
) {}
