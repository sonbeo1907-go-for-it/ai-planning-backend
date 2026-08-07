package com.codegym.aiplanning.controller.admin.dto.course;

import com.codegym.aiplanning.entity.course.ClassStatus;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChangeClassStatusRequest(
        @NotNull(message = "Trạng thái lớp học không được để trống")
        @Schema(description = "Trạng thái mới của lớp học", example = "ACTIVE")
        ClassStatus status,

        @NotNull(message = "Version không được để trống")
        @Schema(description = "Phiên bản hiện tại của lớp học (Optimistic Locking)", example = "0")
        Long version
) {
}
