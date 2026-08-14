package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecordProgressRequest(
        @NotNull(message = "Status is required")
        ProgressEntryStatus status,

        @Min(0)
        Integer actualMinutes,

        @Schema(description = "Actual result or outcome")
        String actualResult,

        @Min(1) @Max(5)
        Integer difficulty,

        @Min(1) @Max(5)
        Integer understandingRating,

        @Schema(description = "Additional notes")
        String note
) {}
