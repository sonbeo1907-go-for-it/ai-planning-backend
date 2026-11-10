package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Ordered Task Steps and their aggregate checklist progress")
public record DailyPlanTaskStepsResponse(
        UUID dailyPlanItemId,
        List<DailyPlanTaskStepResponse> steps,
        TaskStepProgressResponse progress) {

    @Override
    public String toString() {
        return "DailyPlanTaskStepsResponse[dailyPlanItemId=" + dailyPlanItemId
                + ", stepCount=" + (steps == null ? 0 : steps.size())
                + ", progress=" + progress
                + ", personalLearningData=<redacted>]";
    }
}

