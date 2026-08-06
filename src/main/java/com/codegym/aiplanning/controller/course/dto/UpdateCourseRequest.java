package com.codegym.aiplanning.controller.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for updating mutable course details")
public record UpdateCourseRequest(
        @NotBlank
        @Size(max = 150)
        @Schema(example = "Fullstack Java Advanced")
        String name,

        @Size(max = 4000)
        @Schema(description = "General description that may be used as AI context")
        String description) {}
