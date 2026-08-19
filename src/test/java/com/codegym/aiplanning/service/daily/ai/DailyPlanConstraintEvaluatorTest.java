package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyPlanConstraintEvaluatorTest {

    private final DailyPlanConstraintEvaluator evaluator = new DailyPlanConstraintEvaluator();

    @Test
    void evaluate_budgetExceeded_requiresUserDecisionTrue() {
        DailyPlanningContext context = new DailyPlanningContext(LocalDate.now(), 60, null, null, null, null, null);
        DailyPlanAiResponse response = new DailyPlanAiResponse(List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 1", null, DailyTaskCategory.CUSTOM, 40, null, null),
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 2", null, DailyTaskCategory.CUSTOM, 30, null, null)
        ));

        var result = evaluator.evaluate(response, context);

        assertThat(result.requiresUserDecision()).isTrue();
    }

    @Test
    void evaluate_budgetWithinLimit_noProposals_requiresUserDecisionFalse() {
        DailyPlanningContext context = new DailyPlanningContext(LocalDate.now(), 60, null, null, null, null, null);
        DailyPlanAiResponse response = new DailyPlanAiResponse(List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 1", null, DailyTaskCategory.CUSTOM, 40, null, null)
        ));

        var result = evaluator.evaluate(response, context);

        assertThat(result.requiresUserDecision()).isFalse();
    }

    @Test
    void evaluate_hasAiProposals_requiresUserDecisionTrue() {
        DailyPlanningContext context = new DailyPlanningContext(LocalDate.now(), 60, null, null, null, null, null);
        DailyPlanAiResponse response = new DailyPlanAiResponse(List.of(
                new DailyPlanAiResponse.AiPlanItemDto(UUID.randomUUID(), "Task 1", null, DailyTaskCategory.CUSTOM, 40, AiAdjustmentAction.SPLIT, "split reason")
        ));

        var result = evaluator.evaluate(response, context);

        assertThat(result.requiresUserDecision()).isTrue();
    }
}
