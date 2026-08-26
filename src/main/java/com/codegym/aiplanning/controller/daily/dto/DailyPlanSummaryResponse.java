package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyPlanSummaryResponse(
        UUID id,
        LocalDate planDate,
        String timeZoneSnapshot,
        DailyPlanStatus status,
        UUID activeVersionId,
        UUID latestVersionId,
        Integer availableMinutes,
        Integer totalPlannedMinutes,
        int totalItemsCount,
        int completedItemsCount,
        int partiallyCompletedItemsCount,
        int skippedItemsCount,
        double completionPercentage,
        UUID roadmapId,
        Instant createdAt,
        Instant updatedAt) {

    public static DailyPlanSummaryResponse from(
            DailyPlan plan,
            DailyPlanVersion currentVersion,
            List<DailyPlanItem> items) {
        int earnedPercentage = items.stream()
                .mapToInt(item -> item.getStatus().completionPercentage())
                .sum();
        double completionPercentage = items.isEmpty()
                ? 0.0
                : Math.round((double) earnedPercentage / items.size() * 10.0) / 10.0;
        return new DailyPlanSummaryResponse(
                plan.getId(),
                plan.getPlanDate(),
                plan.getTimeZoneSnapshot(),
                plan.getStatus(),
                plan.getActiveVersionId(),
                currentVersion == null ? null : currentVersion.getId(),
                currentVersion == null ? 0 : currentVersion.getAvailableMinutes(),
                currentVersion == null ? 0 : currentVersion.getTotalPlannedMinutes(),
                items.size(),
                count(items, DailyTaskStatus.COMPLETED),
                count(items, DailyTaskStatus.PARTIALLY_COMPLETED),
                count(items, DailyTaskStatus.SKIPPED),
                completionPercentage,
                plan.getRoadmapId(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    private static int count(List<DailyPlanItem> items, DailyTaskStatus status) {
        return (int) items.stream()
                .filter(item -> item.getStatus() == status)
                .count();
    }
}
