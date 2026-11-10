package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import java.time.Instant;
import java.util.UUID;

public record ProgressEntryResponse(
        UUID id,
        UUID dailyPlanItemId,
        UUID roadmapVersionId,
        UUID learningUnitId,
        ProgressEntryStatus status,
        Integer actualMinutes,
        Integer completionPercentage,
        String actualResult,
        Integer difficulty,
        Integer understandingRating,
        String note,
        UUID supersedesEntryId,
        Instant recordedAt) {

    public static ProgressEntryResponse from(ProgressEntry entry) {
        return new ProgressEntryResponse(
                entry.getId(),
                entry.getDailyPlanItemId(),
                entry.getRoadmapVersionId(),
                entry.getLearningUnitId(),
                entry.getStatus(),
                entry.getActualMinutes(),
                entry.getCompletionPercentage(),
                entry.getActualResult(),
                entry.getDifficulty(),
                entry.getUnderstandingRating(),
                entry.getNote(),
                entry.getSupersedesEntryId(),
                entry.getRecordedAt());
    }

    @Override
    public String toString() {
        return "ProgressEntryResponse[id=" + id
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}
