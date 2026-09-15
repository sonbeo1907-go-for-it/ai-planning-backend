package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStepState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One planned Task Step with its separate runtime completion state")
public record DailyPlanTaskStepResponse(
        UUID id,
        long entityVersion,
        UUID dailyPlanItemId,
        String title,
        String guidance,
        Integer orderIndex,
        Integer estimatedMinutes,
        Boolean required,
        Boolean completed,
        Instant completedAt,
        Long stateVersion) {

    public static DailyPlanTaskStepResponse from(
            DailyPlanTaskStep step,
            DailyPlanTaskStepState state) {
        return new DailyPlanTaskStepResponse(
                step.getId(),
                step.getVersion(),
                step.getDailyPlanItemId(),
                step.getTitle(),
                step.getGuidance(),
                step.getOrderIndex(),
                step.getEstimatedMinutes(),
                step.getRequired(),
                state != null && Boolean.TRUE.equals(state.getCompleted()),
                state != null ? state.getCompletedAt() : null,
                state != null ? state.getVersion() : null);
    }

    @Override
    public String toString() {
        return "DailyPlanTaskStepResponse[id=" + id
                + ", orderIndex=" + orderIndex
                + ", required=" + required
                + ", completed=" + completed
                + ", personalLearningData=<redacted>]";
    }
}
