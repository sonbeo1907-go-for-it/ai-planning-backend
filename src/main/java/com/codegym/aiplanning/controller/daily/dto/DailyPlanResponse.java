package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Daily plan details with task list and completion percentage (US-TSK-01-MANUAL)")
public record DailyPlanResponse(
        UUID id,
        UUID userId,
        LocalDate planDate,
        String timeZoneSnapshot,
        DailyPlanStatus status,
        UUID activeVersionId,
        UUID latestVersionId,
        Integer availableMinutes,
        Integer totalPlannedMinutes,
        Integer totalItemsCount,
        Integer completedItemsCount,
        Double completionPercentage,
        List<DailyPlanItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        UUID roadmapId
) {
    public static DailyPlanResponse of(
            DailyPlan plan,
            UUID latestVersionId,
            Integer availableMinutes,
            Integer totalPlannedMinutes,
            List<DailyPlanItemResponse> items) {
        int total = items != null ? items.size() : 0;
        int completed = items != null ? (int) items.stream().filter(i -> i.status() == DailyTaskStatus.COMPLETED).count() : 0;
        int earnedPercentage = items != null
                ? items.stream()
                        .mapToInt(item -> item.status().completionPercentage())
                        .sum()
                : 0;
        double percentage = total > 0
                ? Math.round((double) earnedPercentage / total * 10.0) / 10.0
                : 0.0;

        return new DailyPlanResponse(
                plan.getId(),
                plan.getUserId(),
                plan.getPlanDate(),
                plan.getTimeZoneSnapshot(),
                plan.getStatus(),
                plan.getActiveVersionId(),
                latestVersionId,
                availableMinutes != null ? availableMinutes : 60,
                totalPlannedMinutes != null ? totalPlannedMinutes : 0,
                total,
                completed,
                percentage,
                items != null ? items : List.of(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getRoadmapId()
        );
    }
}
