package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Add one actionable step to a DRAFT Daily Plan task")
public record CreateTaskStepRequest(
        @NotBlank(message = "Task Step title is required")
        @Size(max = 255, message = "Task Step title must not exceed 255 characters")
        String title,

        @Size(max = 4000, message = "Task Step guidance must not exceed 4000 characters")
        String guidance,

        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        @Min(value = 1, message = "Estimated minutes must be at least 1")
        @Max(value = 1440, message = "Estimated minutes cannot exceed 1440")
        Integer estimatedMinutes,

        @Schema(description = "Whether the step contributes to required-step progress", defaultValue = "true")
        Boolean required) {

    @Override
    public String toString() {
        return "CreateTaskStepRequest[orderIndex=" + orderIndex
                + ", estimatedMinutes=" + estimatedMinutes
                + ", required=" + required
                + ", personalLearningData=<redacted>]";
    }
}

