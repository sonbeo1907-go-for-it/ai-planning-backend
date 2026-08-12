package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a Milestone inside a draft Roadmap version")
public record CreateMilestoneRequest(
        @NotBlank(message = "Milestone title is required")
        @Size(max = 200, message = "Milestone title must not exceed 200 characters")
        @Schema(example = "Week 1")
        String title,

        @Size(max = 4000, message = "Milestone description must not exceed 4000 characters")
        String description,

        @Min(value = 0, message = "Order index must not be negative")
        Integer orderIndex) {

    @Override
    public String toString() {
        return "CreateMilestoneRequest[personalLearningData=<redacted>]";
    }
}
