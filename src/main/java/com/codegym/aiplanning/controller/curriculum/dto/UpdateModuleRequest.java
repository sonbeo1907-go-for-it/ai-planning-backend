package com.codegym.aiplanning.controller.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for updating module details (US-MOD-02)")
public record UpdateModuleRequest(
        @NotBlank
        @Size(max = 150)
        @Schema(example = "Java Core Basic (Cập nhật)")
        String name,

        @Size(max = 4000)
        @Schema(description = "Mô tả phạm vi tổng quan của module")
        String description) {}
