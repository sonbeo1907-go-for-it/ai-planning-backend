package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.util.List;
import java.util.UUID;

public record DailyPlanAiResponse(
    List<AiPlanItemDto> items
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
}
