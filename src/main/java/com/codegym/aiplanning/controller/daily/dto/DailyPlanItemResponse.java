package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Daily plan task item details (US-TSK-01-MANUAL)")
public record DailyPlanItemResponse(
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
        String roadmapItemTitle,
        UUID learningUnitId,
        String learningUnitTitle,
        UUID parentTopicId,
        String parentTopicTitle,
        RoadmapItemReferenceResponse roadmapItem,
        StudyUnitReferenceResponse studyUnit,
        AiAdjustmentAction aiAdjustmentAction,
        String aiAdjustmentReason
) {
    public static DailyPlanItemResponse from(DailyPlanItem item) {
        return from(item, null, null, null, null);
    }

    public static DailyPlanItemResponse from(
            DailyPlanItem item,
            UUID learningUnitId,
            String roadmapItemTitle,
            UUID parentTopicId,
            String parentTopicTitle) {
        return new DailyPlanItemResponse(
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
                roadmapItemTitle,
                learningUnitId,
                learningUnitId == null ? null : roadmapItemTitle,
                parentTopicId,
                parentTopicTitle,
                parentTopicId == null
                        ? null
                        : new RoadmapItemReferenceResponse(
                                parentTopicId, parentTopicTitle),
                learningUnitId == null
                        ? null
                        : new StudyUnitReferenceResponse(
                                learningUnitId, roadmapItemTitle),
                item.getAiAdjustmentAction(),
                item.getAiAdjustmentReason()
        );
    }
}
