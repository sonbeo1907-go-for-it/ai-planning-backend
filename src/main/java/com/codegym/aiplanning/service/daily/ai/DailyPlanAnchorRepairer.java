package com.codegym.aiplanning.service.daily.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Repairs only transient AI validation metadata, never planned task or step content. */
public class DailyPlanAnchorRepairer {

    private final LearningUnitAnchorMatcher matcher = new LearningUnitAnchorMatcher();

    public DailyPlanAiResponse repair(
            DailyPlanAiResponse response,
            DailyPlanPromptContext promptContext) {
        if (response == null || response.items() == null) {
            return response;
        }
        Map<UUID, DailyPlanPromptContext.RelevantTopic> topics = promptContext.relevantTopics()
                .stream()
                .collect(Collectors.toMap(
                        DailyPlanPromptContext.RelevantTopic::roadmapItemId,
                        topic -> topic));
        List<DailyPlanAiResponse.AiPlanItemDto> items = new ArrayList<>();
        for (DailyPlanAiResponse.AiPlanItemDto item : response.items()) {
            if (item == null || item.steps() == null) {
                items.add(item);
                continue;
            }
            DailyPlanPromptContext.RelevantTopic topic = topics.get(item.roadmapItemId());
            if (topic == null) {
                items.add(item);
                continue;
            }
            List<DailyPlanAiResponse.AiTaskStepDto> steps = new ArrayList<>();
            for (DailyPlanAiResponse.AiTaskStepDto step : item.steps()) {
                if (step == null) {
                    steps.add(null);
                    continue;
                }
                String anchor = matcher.repairAnchor(
                        step.scopeAnchor(),
                        step.title(),
                        step.guidance(),
                        topic.title(),
                        topic.description());
                steps.add(Objects.equals(anchor, step.scopeAnchor())
                        ? step
                        : new DailyPlanAiResponse.AiTaskStepDto(
                                step.title(),
                                step.guidance(),
                                step.orderIndex(),
                                step.estimatedMinutes(),
                                step.required(),
                                step.actionType(),
                                anchor));
            }
            items.add(new DailyPlanAiResponse.AiPlanItemDto(
                    item.roadmapItemId(),
                    item.title(),
                    item.description(),
                    item.category(),
                    item.plannedMinutes(),
                    item.aiAdjustmentAction(),
                    item.aiAdjustmentReason(),
                                steps));
        }
        return new DailyPlanAiResponse(response.summary(), items, response.adjustments());
    }
}
