package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Request to record a completed Pomodoro study session (US-TSK-02)")
public record RecordPomodoroSessionRequest(
        @Min(value = 1, message = "Completed minutes must be at least 1")
        @Max(value = 180, message = "Completed minutes cannot exceed 180")
        @Schema(description = "Completed Pomodoro focus minutes to accumulate", example = "25")
        Integer completedMinutes
) {}
