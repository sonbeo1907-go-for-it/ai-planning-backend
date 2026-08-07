package com.codegym.aiplanning.controller.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClassRequest(
        @NotNull(message = "Course ID is required") UUID courseId,
        @NotBlank(message = "Class code is required") @Size(max = 50, message = "Class code must not exceed 50 characters") String code,
        @NotBlank(message = "Class name is required") @Size(max = 150, message = "Class name must not exceed 150 characters") String name,
        String description) {}
