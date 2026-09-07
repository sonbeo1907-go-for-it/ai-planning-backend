package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyPlanValidatorTest {

    private final DailyPlanValidator validator = new DailyPlanValidator();
    private final UUID roadmapItemId = UUID.randomUUID();
    private final DailyPlanningContext context = context(60);

    @Test
    void validateResponse_acceptsValidInBudgetPlan() {
        DailyPlanAiResponse response = response(60, roadmapItemId);

        assertThatCode(() -> validator.validateResponse(response, context))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponse_rejectsOverBudgetPlan() {
        DailyPlanAiResponse response = response(61, roadmapItemId);

        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("available-time budget");
    }

    @Test
    void validateResponse_rejectsRoadmapItemOutsideActiveVersion() {
        DailyPlanAiResponse response = response(30, UUID.randomUUID());

        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("ACTIVE RoadmapVersion");
    }

    @Test
    void validateResponse_rejectsCustomCategoryFromAi() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Plan",
                List.of(new DailyPlanAiResponse.AiPlanItemDto(
                        null,
                        "Custom task",
                        null,
                        DailyTaskCategory.CUSTOM,
                        30,
                        null,
                        null)),
                List.of());

        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("REVIEW, NEW_MATERIAL, or PRACTICE");
    }

    @Test
    void validateResponse_rejectsMoreThanOneReviewTask() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Plan",
                List.of(
                        item("Review one", DailyTaskCategory.REVIEW, 10),
                        item("Review two", DailyTaskCategory.REVIEW, 5)),
                List.of());

        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("at most one REVIEW");
    }

    @Test
    void validateResponse_rejectsReviewAboveThirtyPercentOfAvailableTime() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Plan",
                List.of(
                        item("Long review", DailyTaskCategory.REVIEW, 19),
                        item("Practice", DailyTaskCategory.PRACTICE, 20)),
                List.of());

        assertThatThrownBy(() -> validator.validateResponse(response, context))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("exceeds 30%");
    }

    @Test
    void validateResponse_acceptsOneReviewAtThirtyPercent() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Plan",
                List.of(
                        item("Review", DailyTaskCategory.REVIEW, 18),
                        item("Practice", DailyTaskCategory.PRACTICE, 30)),
                List.of());

        assertThatCode(() -> validator.validateResponse(response, context))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponse_rejectsCompletedTopicAsNewMaterial() {
        DailyPlanPromptContext promptContext = new DailyPlanPromptContext(
                LocalDate.now(),
                "UTC",
                60,
                "Roadmap",
                List.of(new DailyPlanPromptContext.RelevantTopic(
                        roadmapItemId,
                        "Completed topic",
                        60,
                        DailyPlanPromptContext.TopicPriority.REVIEW_DUE,
                        true)),
                null,
                List.of(),
                List.of(),
                List.of());
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Plan",
                List.of(item("Repeat as new", DailyTaskCategory.NEW_MATERIAL, 15)),
                List.of());

        assertThatThrownBy(() -> validator.validateResponse(response, promptContext))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("only as REVIEW");
    }

    private DailyPlanAiResponse response(int plannedMinutes, UUID itemId) {
        return new DailyPlanAiResponse(
                "Plan",
                List.of(new DailyPlanAiResponse.AiPlanItemDto(
                        itemId,
                        "Practice",
                        null,
                        DailyTaskCategory.PRACTICE,
                        plannedMinutes,
                        null,
                        null)),
                List.of());
    }

    private DailyPlanAiResponse.AiPlanItemDto item(
            String title,
            DailyTaskCategory category,
            int plannedMinutes) {
        return new DailyPlanAiResponse.AiPlanItemDto(
                roadmapItemId,
                title,
                null,
                category,
                plannedMinutes,
                null,
                null);
    }

    private DailyPlanningContext context(int availableMinutes) {
        return new DailyPlanningContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "UTC",
                availableMinutes,
                new DailyPlanningContext.RoadmapContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "Roadmap",
                        null,
                        List.of(new DailyPlanningContext.RoadmapTopic(
                                roadmapItemId,
                                UUID.randomUUID(),
                                "Milestone",
                                "Topic",
                                null,
                                60,
                                0))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null);
    }
}
