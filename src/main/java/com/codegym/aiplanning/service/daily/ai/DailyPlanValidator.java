package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DailyPlanValidator {

    public void validateResponse(DailyPlanAiResponse response, DailyPlanningContext context) {
        if (response == null
                || response.summary() == null
                || response.summary().isBlank()
                || response.summary().length() > 4000) {
            throw invalid("AI response summary is missing or too long.");
        }
        if (response.items() == null || response.items().isEmpty() || response.items().size() > 50) {
            throw invalid("AI response must contain between 1 and 50 planned items.");
        }

        Set<UUID> activeRoadmapItemIds = context.roadmap().topics().stream()
                .map(DailyPlanningContext.RoadmapTopic::roadmapItemId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> itemKeys = new HashSet<>();
        int totalMinutes = 0;
        for (DailyPlanAiResponse.AiPlanItemDto item : response.items()) {
            requireText(item.title(), 255, "item.title");
            requireOptionalText(item.description(), 4000, "item.description");
            if (item.plannedMinutes() == null
                    || item.plannedMinutes() <= 0
                    || item.plannedMinutes() > 1440) {
                throw invalid("item.plannedMinutes must be between 1 and 1440.");
            }
            if (item.category() == null || item.category() == DailyTaskCategory.CUSTOM) {
                throw invalid("AI items must use REVIEW, NEW_MATERIAL, or PRACTICE.");
            }
            if (item.roadmapItemId() != null
                    && !activeRoadmapItemIds.contains(item.roadmapItemId())) {
                throw invalid("An AI item references a Roadmap Item outside the ACTIVE RoadmapVersion.");
            }
            if (item.aiAdjustmentAction() != null) {
                if (item.aiAdjustmentAction() != AiAdjustmentAction.CARRY_OVER
                        && item.aiAdjustmentAction() != AiAdjustmentAction.SPLIT) {
                    throw invalid("Only CARRY_OVER or SPLIT can describe an item kept in today's plan.");
                }
                requireText(item.aiAdjustmentReason(), 1000, "item.aiAdjustmentReason");
            } else if (item.aiAdjustmentReason() != null && !item.aiAdjustmentReason().isBlank()) {
                throw invalid("item.aiAdjustmentReason requires an adjustment action.");
            }
            String itemKey = item.title().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|"
                    + item.roadmapItemId();
            if (!itemKeys.add(itemKey)) {
                throw invalid("AI response contains a duplicate planned item.");
            }
            totalMinutes += item.plannedMinutes();
        }

        if (totalMinutes > context.availableMinutes()) {
            throw invalid("AI planned minutes exceed the user's available-time budget.");
        }

        if (response.adjustments() == null || response.adjustments().size() > 50) {
            throw invalid("AI response adjustments are missing or too numerous.");
        }
        Set<UUID> unfinishedIds = context.unfinishedTasks().stream()
                .map(DailyPlanningContext.UnfinishedTask::dailyPlanItemId)
                .collect(java.util.stream.Collectors.toSet());
        for (DailyPlanAiResponse.AiAdjustmentProposalDto adjustment : response.adjustments()) {
            requireText(adjustment.title(), 255, "adjustment.title");
            requireText(adjustment.reason(), 1000, "adjustment.reason");
            if (adjustment.action() == null
                    || adjustment.action() == AiAdjustmentAction.CARRY_OVER) {
                throw invalid("A separate adjustment must use SPLIT, RESCHEDULE, or DROP.");
            }
            if (adjustment.sourceDailyPlanItemId() != null
                    && !unfinishedIds.contains(adjustment.sourceDailyPlanItemId())) {
                throw invalid("An adjustment references a task outside the previous unfinished set.");
            }
            if (adjustment.proposedMinutes() != null
                    && (adjustment.proposedMinutes() <= 0
                            || adjustment.proposedMinutes() > 1440)) {
                throw invalid("adjustment.proposedMinutes must be between 1 and 1440 when present.");
            }
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw invalid(field + " must be a non-blank string within its length limit.");
        }
    }

    private void requireOptionalText(String value, int maxLength, String field) {
        if (value != null && value.trim().length() > maxLength) {
            throw invalid(field + " exceeds its length limit.");
        }
    }

    private InvalidAiDailyPlanResponseException invalid(String message) {
        return new InvalidAiDailyPlanResponseException(message);
    }
}
