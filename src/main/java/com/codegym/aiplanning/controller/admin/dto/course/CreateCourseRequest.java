package com.codegym.aiplanning.controller.admin.dto.course;

import com.codegym.aiplanning.entity.course.CourseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Z0-9_\\-]+$") String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull CourseStatus status
) {}
