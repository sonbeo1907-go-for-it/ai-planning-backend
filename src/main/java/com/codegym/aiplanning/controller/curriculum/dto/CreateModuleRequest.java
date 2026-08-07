package com.codegym.aiplanning.controller.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a module inside a course (US-MOD-01)")
public record CreateModuleRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(
                regexp = "[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*",
                message = "must contain only letters, digits and single underscores")
        @Schema(example = "JAVA_CORE")
        String code,

        @NotBlank
        @Size(max = 150)
        @Schema(example = "Module 1: Java Core Căn bản")
        String name,

        @Size(max = 4000)
        @Schema(description = "Mô tả phạm vi tổng quan của module")
        String description,

        @Min(value = 1, message = "sequenceNumber must be greater than 0")
        @Schema(description = "Thứ tự trong lộ trình (tùy chọn, tự động tính nếu không truyền)", example = "1")
        Integer sequenceNumber) {}
