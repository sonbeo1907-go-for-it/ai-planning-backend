package com.codegym.aiplanning.controller.course.dto;

import com.codegym.aiplanning.entity.course.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu thay đổi trạng thái lớp học")
public record ChangeClassStatusRequest(
        @Schema(description = "Trạng thái mới của lớp học", example = "ACTIVE") 
        @NotNull(message = "Status is required.") 
        ClassStatus status,
        
        @Schema(description = "Phiên bản hiện tại để optimistic locking", example = "1")
        @NotNull(message = "Version is required for optimistic locking.")
        Long version) {}
