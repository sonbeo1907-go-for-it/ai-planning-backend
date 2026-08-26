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
                null);
    }
}
