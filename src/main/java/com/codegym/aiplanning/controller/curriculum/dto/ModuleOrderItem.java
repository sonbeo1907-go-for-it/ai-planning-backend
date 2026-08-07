package com.codegym.aiplanning.controller.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Single module ordering item (US-MOD-03)")
public record ModuleOrderItem(
        @NotNull
        @Schema(description = "UUID của Module")
        UUID moduleId,

        @NotNull
        @Min(1)
        @Schema(description = "Thứ tự bài học mới (sequenceNumber > 0)", example = "1")
        Integer sequenceNumber) {}
