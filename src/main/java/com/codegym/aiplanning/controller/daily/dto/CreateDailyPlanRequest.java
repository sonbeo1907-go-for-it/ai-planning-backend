package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Request to create a new daily plan (US-TSK-01-MANUAL)")
public record CreateDailyPlanRequest(
        @NotNull(message = "Plan date is required")
        @Schema(description = "Date of the plan (YYYY-MM-DD)", example = "2026-08-12")
        LocalDate planDate,

        @Min(value = 1, message = "Available minutes must be at least 1")
        @Max(value = 1440, message = "Available minutes cannot exceed 1440")
        @Schema(description = "Available minutes committed for the day", example = "120")
        Integer availableMinutes
) {}
