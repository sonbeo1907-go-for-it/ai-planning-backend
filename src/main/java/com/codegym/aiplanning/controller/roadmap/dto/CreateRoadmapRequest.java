package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a user-owned manual Roadmap and its first draft version")
public record CreateRoadmapRequest(
        @NotBlank(message = "Roadmap title is required")
        @Size(max = 200, message = "Roadmap title must not exceed 200 characters")
        @Schema(example = "Backend with Java")
        String title,

        @Size(max = 4000, message = "Roadmap description must not exceed 4000 characters")
        String description) {

    @Override
    public String toString() {
        return "CreateRoadmapRequest[personalLearningData=<redacted>]";
    }
}
