package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Replace the editable content and order of one Task Step")
public record UpdateTaskStepRequest(
        @NotNull(message = "Task Step entity version is required")
        @Min(value = 0, message = "Task Step entity version must not be negative")
        Long entityVersion,

        @NotBlank(message = "Task Step title is required")
        @Size(max = 255, message = "Task Step title must not exceed 255 characters")
        String title,

        @Size(max = 4000, message = "Task Step guidance must not exceed 4000 characters")
        String guidance,

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        @Min(value = 1, message = "Estimated minutes must be at least 1")
        @Max(value = 1440, message = "Estimated minutes cannot exceed 1440")
        Integer estimatedMinutes,

        @NotNull(message = "Required flag is required")
        Boolean required) {

    @Override
    public String toString() {
        return "UpdateTaskStepRequest[entityVersion=" + entityVersion
                + ", orderIndex=" + orderIndex
                + ", estimatedMinutes=" + estimatedMinutes
                + ", required=" + required
                + ", personalLearningData=<redacted>]";
    }
}
