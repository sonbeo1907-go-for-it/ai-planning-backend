package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a Topic inside a Milestone of a draft Roadmap version")
public record CreateTopicRequest(
        @NotBlank(message = "Topic title is required")
        @Size(max = 200, message = "Topic title must not exceed 200 characters")
        @Schema(example = "Java OOP fundamentals")
        String title,

        @Size(max = 4000, message = "Topic description must not exceed 4000 characters")
        String description,

        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex,

        @NotNull(message = "Estimated minutes are required")
        @Min(value = 1, message = "Estimated minutes must be at least 1")
        @Max(value = 10080, message = "Estimated minutes must not exceed 10080")
        Integer estimatedMinutes) {

    @Override
    public String toString() {
        return "CreateTopicRequest[personalLearningData=<redacted>]";
    }
}
