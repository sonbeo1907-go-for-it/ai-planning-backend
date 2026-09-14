package com.codegym.aiplanning.controller.daily.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DailyPlanTaskProgressHistoryResponse(
        UUID dailyPlanItemId,
        UUID dailyPlanVersionId,
        String taskTitle,
        Instant removedAt,
        List<ProgressEntryResponse> entries) {

    @Override
    public String toString() {
        return "DailyPlanTaskProgressHistoryResponse[dailyPlanItemId="
                + dailyPlanItemId
                + ", removed="
                + (removedAt != null)
                + ", personalLearningData=<redacted>]";
    }
}
