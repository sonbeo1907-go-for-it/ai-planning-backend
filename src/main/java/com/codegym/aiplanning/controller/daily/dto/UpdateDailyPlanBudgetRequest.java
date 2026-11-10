package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Update the available-time snapshot of an exact DRAFT Daily Plan version")
public record UpdateDailyPlanBudgetRequest(
        @NotNull(message = "Available minutes are required")
        @Min(value = 15, message = "Available minutes must be at least 15")
        @Max(value = 480, message = "Available minutes must not exceed 480")
        @Schema(minimum = "15", maximum = "480", multipleOf = 15, example = "90")
        Integer availableMinutes,

        @NotNull(message = "Entity version is required")
        @PositiveOrZero(message = "Entity version must not be negative")
        @Schema(description = "Last Daily Plan version entity version observed by the client")
        Long entityVersion) {}
