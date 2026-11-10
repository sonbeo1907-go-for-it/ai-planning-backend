package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Create an executable Learning Unit inside a Topic")
public record CreateLearningUnitRequest(
        @NotBlank(message = "Learning Unit title is required")
        @Size(max = 200, message = "Learning Unit title must not exceed 200 characters")
        @Schema(example = "Understand encapsulation")
        String title,

        @Size(max = 4000, message = "Learning Unit description must not exceed 4000 characters")
        String description,

        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        @NotNull(message = "Estimated minutes are required")
        @Min(value = 1, message = "Estimated minutes must be at least 1")
        @Max(value = 1440, message = "Estimated minutes must not exceed 1440")
        Integer estimatedMinutes) {

    @Override
    public String toString() {
        return "CreateLearningUnitRequest[personalLearningData=<redacted>]";
    }
}
