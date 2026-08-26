package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Daily plan task detail including optional AI suggestion (US-TSK-AI)")
public record DailyPlanItemDetailResponse(
        UUID id,
        UUID versionId,
        DailyTaskCategory category,
        String title,
        String description,
        Integer plannedMinutes,
        Integer orderIndex,
        DailyTaskStatus status,
        Instant completedAt,
        Instant createdAt,
        UUID roadmapItemId,
        AiAdjustmentAction aiAdjustmentAction,
        String aiAdjustmentReason,
        DailyPlanItemAiSuggestionResponse aiSuggestion) {

    public static DailyPlanItemDetailResponse of(
            DailyPlanItem item, DailyPlanItemAiSuggestionResponse aiSuggestion) {
        return new DailyPlanItemDetailResponse(
                item.getId(),
                item.getDailyPlanVersionId(),
                item.getCategory(),
                item.getTitle(),
                item.getDescription(),
                item.getPlannedMinutes(),
                item.getOrderIndex(),
                item.getStatus(),
                item.getCompletedAt(),
                item.getCreatedAt(),
                item.getRoadmapItemId(),
                item.getAiAdjustmentAction(),
                item.getAiAdjustmentReason(),
                aiSuggestion);
    }
}
