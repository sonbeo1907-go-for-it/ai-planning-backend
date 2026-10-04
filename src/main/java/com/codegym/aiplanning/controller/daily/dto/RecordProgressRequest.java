package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecordProgressRequest(
        @NotNull(message = "Status is required")
        ProgressEntryStatus status,

        @Schema(description = "Exact task completion percentage. Required from 1 to 99 for PARTIALLY_COMPLETED; COMPLETED is 100 and SKIPPED is 0.")
        @Min(0) @Max(100)
        Integer completionPercentage,

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
) {
    public RecordProgressRequest(
            ProgressEntryStatus status,
            Integer actualMinutes,
            String actualResult,
            Integer difficulty,
            Integer understandingRating,
            String note) {
        this(
                status,
                null,
                actualMinutes,
                actualResult,
                difficulty,
                understandingRating,
                note);
    }
}
