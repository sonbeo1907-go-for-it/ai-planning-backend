package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Replace editable values of a draft Milestone or Topic")
public record UpdateRoadmapItemRequest(
        @NotBlank(message = "Item title is required")
        @Size(max = 200, message = "Item title must not exceed 200 characters")
        String title,

        @Size(max = 4000, message = "Item description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        @Min(value = 1, message = "Estimated minutes must be at least 1")
        @Max(value = 10080, message = "Estimated minutes must not exceed 10080")
        Integer estimatedMinutes) {

    @Override
    public String toString() {
        return "UpdateRoadmapItemRequest[personalLearningData=<redacted>]";
    }
}
