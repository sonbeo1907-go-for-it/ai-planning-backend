package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to check or update task completion status (US-TSK-01-MANUAL)")
public record UpdateTaskStatusRequest(
        @NotNull(message = "Task status is required")
        @Schema(description = "New status of the task", example = "COMPLETED")
        DailyTaskStatus status,

        @Min(value = 0, message = "Actual minutes cannot be negative")
        @Max(value = 1440, message = "Actual minutes cannot exceed 1440")
        @Schema(description = "Actual time spent in minutes", example = "30")
        Integer actualMinutes
) {}
