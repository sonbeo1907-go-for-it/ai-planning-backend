package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DailyPlanValidator {

    static final int MAX_REVIEW_PERCENT = 30;
    static final int MAX_TASK_STEPS = 8;
    private final AiTaskStepQualityValidator stepQualityValidator =
            new AiTaskStepQualityValidator();

    public void validateResponse(DailyPlanAiResponse response, DailyPlanningContext context) {
        Set<UUID> activeRoadmapItemIds = context.roadmap().topics().stream()
                .map(DailyPlanningContext.RoadmapTopic::roadmapItemId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> unfinishedIds = context.unfinishedTasks().stream()
                .map(DailyPlanningContext.UnfinishedTask::dailyPlanItemId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, StepReferenceTitles> referenceTitles = context.roadmap().topics().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyPlanningContext.RoadmapTopic::roadmapItemId,
                        topic -> new StepReferenceTitles(
                                topic.title(),
                                topic.description(),
                                topic.parentTopicTitle())));
        validateResponse(
                response,
                context.availableMinutes(),
                activeRoadmapItemIds,
                unfinishedIds,
                Set.of(),
                referenceTitles);
    }

    public void validateResponse(DailyPlanAiResponse response, DailyPlanPromptContext context) {
        Set<UUID> suppliedRoadmapItemIds = context.relevantTopics().stream()
                .map(DailyPlanPromptContext.RelevantTopic::roadmapItemId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> suppliedUnfinishedIds = context.unresolvedTasks().stream()
                .map(DailyPlanPromptContext.UnresolvedTask::dailyPlanItemId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> completedReviewIds = context.relevantTopics().stream()
                .filter(DailyPlanPromptContext.RelevantTopic::completed)
                .map(DailyPlanPromptContext.RelevantTopic::roadmapItemId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, StepReferenceTitles> referenceTitles = context.relevantTopics().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyPlanPromptContext.RelevantTopic::roadmapItemId,
                        topic -> new StepReferenceTitles(
                                topic.title(),
                                topic.description(),
                                topic.parentTopicTitle())));
        validateResponse(
                response,
                context.availableMinutes(),
                suppliedRoadmapItemIds,
                suppliedUnfinishedIds,
                completedReviewIds,
                referenceTitles);
    }

    private void validateResponse(
            DailyPlanAiResponse response,
            int availableMinutes,
            Set<UUID> allowedRoadmapItemIds,
            Set<UUID> allowedUnfinishedIds,
            Set<UUID> completedReviewIds,
            Map<UUID, StepReferenceTitles> referenceTitles) {
        if (response == null
                || response.summary() == null
                || response.summary().isBlank()
                || response.summary().length() > 4000) {
            throw invalid("AI response summary is missing or too long.");
        }
        if (response.items() == null || response.items().isEmpty() || response.items().size() > 50) {
            throw invalid("AI response must contain between 1 and 50 planned items.");
        }

        Set<String> itemKeys = new HashSet<>();
        int totalMinutes = 0;
        int reviewCount = 0;
        int reviewMinutes = 0;
        List<InvalidAiDailyPlanResponseException.StepLocation> anchorFailures =
                new ArrayList<>();
        for (int itemIndex = 0; itemIndex < response.items().size(); itemIndex++) {
            DailyPlanAiResponse.AiPlanItemDto item = response.items().get(itemIndex);
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
                    && !allowedRoadmapItemIds.contains(item.roadmapItemId())) {
                throw invalid(
                        "An AI item references a Roadmap Item outside the supplied ACTIVE RoadmapVersion context.");
            }
            if (item.roadmapItemId() != null
                    && completedReviewIds.contains(item.roadmapItemId())
                    && item.category() != DailyTaskCategory.REVIEW) {
                throw invalid("A completed Roadmap Item may return only as REVIEW work.");
            }
            if (item.category() == DailyTaskCategory.REVIEW) {
                reviewCount++;
                reviewMinutes += item.plannedMinutes();
                if (item.roadmapItemId() == null) {
                    throw invalid("A REVIEW item must reference an active Roadmap topic.");
                }
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
            validateSteps(
                    item,
                    referenceTitles.get(item.roadmapItemId()),
                    itemIndex,
                    anchorFailures);
            String itemKey = item.title().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|"
                    + item.roadmapItemId();
            if (!itemKeys.add(itemKey)) {
                throw invalid("AI response contains a duplicate planned item.");
            }
            totalMinutes += item.plannedMinutes();
        }

        if (!anchorFailures.isEmpty()) {
            throw new InvalidAiDailyPlanResponseException(
                    "AI Task Step scope validation failed.",
                    anchorFailures.get(0).reason(),
                    anchorFailures);
        }

        if (totalMinutes > availableMinutes) {
            throw invalid("AI planned minutes exceed the user's available-time budget.");
        }
        if (reviewCount > 1) {
            throw invalid("AI response may contain at most one REVIEW item.");
        }
        int maxReviewMinutes = availableMinutes * MAX_REVIEW_PERCENT / 100;
        if (reviewMinutes > maxReviewMinutes) {
            throw invalid("AI REVIEW work exceeds 30% of the user's available-time budget.");
        }

        if (response.adjustments() == null || response.adjustments().size() > 50) {
            throw invalid("AI response adjustments are missing or too numerous.");
        }
        for (DailyPlanAiResponse.AiAdjustmentProposalDto adjustment : response.adjustments()) {
            requireText(adjustment.title(), 255, "adjustment.title");
            requireText(adjustment.reason(), 1000, "adjustment.reason");
            if (adjustment.action() == null
                    || adjustment.action() == AiAdjustmentAction.CARRY_OVER) {
                throw invalid("A separate adjustment must use SPLIT, RESCHEDULE, or DROP.");
            }
            if (adjustment.sourceDailyPlanItemId() != null
                    && !allowedUnfinishedIds.contains(adjustment.sourceDailyPlanItemId())) {
                throw invalid("An adjustment references a task outside the supplied unresolved set.");
            }
            if (adjustment.proposedMinutes() != null
                    && (adjustment.proposedMinutes() <= 0
                            || adjustment.proposedMinutes() > 1440)) {
                throw invalid("adjustment.proposedMinutes must be between 1 and 1440 when present.");
            }
        }
    }

    private void validateSteps(
            DailyPlanAiResponse.AiPlanItemDto item,
            StepReferenceTitles referenceTitles,
            int itemIndex,
            List<InvalidAiDailyPlanResponseException.StepLocation> anchorFailures) {
        if (item.steps() == null
                || item.steps().isEmpty()
                || item.steps().size() > MAX_TASK_STEPS) {
            throw invalid("Each AI Daily Plan item must contain between 1 and 8 Task Steps.");
        }

        Set<String> normalizedTitles = new HashSet<>();
        String normalizedItemTitle = normalizeTitle(item.title());
        String normalizedLearningUnitTitle = referenceTitles == null
                ? ""
                : normalizeTitle(referenceTitles.learningUnitTitle());
        String normalizedParentTopicTitle = referenceTitles == null
                ? ""
                : normalizeTitle(referenceTitles.parentTopicTitle());
        int estimatedTotal = 0;
        int requiredCount = 0;

        for (int stepIndex = 0; stepIndex < item.steps().size(); stepIndex++) {
            DailyPlanAiResponse.AiTaskStepDto step = item.steps().get(stepIndex);
            if (step == null) {
                throw invalid("Task Step entries must be JSON objects.");
            }
            requireText(step.title(), 255, "step.title");
            requireOptionalText(step.guidance(), 4000, "step.guidance");
            if (step.orderIndex() == null || step.orderIndex() < 0) {
                throw invalid("step.orderIndex must not be negative.");
            }
            if (step.estimatedMinutes() != null) {
                if (step.estimatedMinutes() <= 0
                        || step.estimatedMinutes() > 1440) {
                    throw invalid("step.estimatedMinutes must be between 1 and 1440 when present.");
                }
                estimatedTotal += step.estimatedMinutes();
            }
            if (step.required() == null) {
                throw invalid("step.required is required.");
            }
            if (step.required()) {
                requiredCount++;
            }

            String normalizedStepTitle = normalizeTitle(step.title());
            if (!normalizedTitles.add(normalizedStepTitle)) {
                throw invalid("Task Step titles must be unique within one AI task.");
            }
            if (normalizedStepTitle.equals(normalizedItemTitle)
                    || (!normalizedLearningUnitTitle.isEmpty()
                            && normalizedStepTitle.equals(normalizedLearningUnitTitle))
                    || (!normalizedParentTopicTitle.isEmpty()
                            && normalizedStepTitle.equals(normalizedParentTopicTitle))) {
                throw invalid(
                        "A Task Step must not repeat its task, Learning Unit, or parent Topic title.");
            }
            try {
                stepQualityValidator.validate(
                        step,
                        item.title(),
                        referenceTitles == null ? null : referenceTitles.learningUnitTitle(),
                        referenceTitles == null ? null : referenceTitles.learningUnitDescription(),
                        referenceTitles == null ? null : referenceTitles.parentTopicTitle());
            } catch (InvalidAiDailyPlanResponseException exception) {
                if (!exception.reason().isStepAnchorFailure()) {
                    throw exception;
                }
                if (anchorFailures.size() < 5) {
                    anchorFailures.add(new InvalidAiDailyPlanResponseException.StepLocation(
                            itemIndex, stepIndex, exception.reason()));
                }
            }
        }

        if (requiredCount == 0) {
            throw invalid("An AI task must contain at least one required Task Step.");
        }
        if (estimatedTotal > item.plannedMinutes()) {
            throw invalid("Task Step estimates exceed their parent AI task's planned minutes.");
        }
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
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

    private record StepReferenceTitles(
            String learningUnitTitle,
            String learningUnitDescription,
            String parentTopicTitle) {}
}
