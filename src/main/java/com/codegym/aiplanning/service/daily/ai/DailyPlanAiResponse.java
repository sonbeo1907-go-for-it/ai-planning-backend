package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.util.List;
import java.util.UUID;

public record DailyPlanAiResponse(
    String summary,
    List<AiPlanItemDto> items,
    List<AiAdjustmentProposalDto> adjustments
) {
    public record AiPlanItemDto(
        UUID roadmapItemId,
        String title,
        String description,
        DailyTaskCategory category,
        Integer plannedMinutes,
        AiAdjustmentAction aiAdjustmentAction,
        String aiAdjustmentReason
    ) {}

    public record AiAdjustmentProposalDto(
        UUID sourceDailyPlanItemId,
        String title,
        AiAdjustmentAction action,
        String reason,
        Integer proposedMinutes
    ) {}
}
