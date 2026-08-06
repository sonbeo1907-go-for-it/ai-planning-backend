package com.codegym.aiplanning.controller.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a course")
public record CreateCourseRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(
                regexp = "[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*",
                message = "must contain only letters, digits and single underscores")
        @Schema(example = "FULLSTACK_JAVA")
        String code,

        @NotBlank
        @Size(max = 150)
        @Schema(example = "Fullstack Java")
        String name,

        @Size(max = 4000)
        @Schema(description = "General description that may be used as AI context")
        String description) {}
