package com.codegym.aiplanning.controller.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Update owner-controlled Roadmap metadata")
public record UpdateRoadmapRequest(
        @NotBlank(message = "Roadmap title is required")
        @Size(max = 200, message = "Roadmap title must not exceed 200 characters")
        String title,

        @Size(max = 4000, message = "Roadmap description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Entity version is required")
        @PositiveOrZero(message = "Entity version must not be negative")
        Long entityVersion) {

    @Override
    public String toString() {
        return "UpdateRoadmapRequest[entityVersion=" + entityVersion
                + ", personalLearningData=<redacted>]";
    }
}
