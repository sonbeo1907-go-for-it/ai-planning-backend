package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.daily.AiAdjustmentAction;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyPlanConstraintEvaluatorTest {

    private final DailyPlanConstraintEvaluator evaluator =
            new DailyPlanConstraintEvaluator();

    @Test
    void evaluate_withoutProposals_doesNotRequireUserDecision() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Balanced",
                List.of(item(null)),
                List.of());

        var result = evaluator.evaluate(response, context());

        assertThat(result.requiresUserDecision()).isFalse();
    }

    @Test
    void evaluate_withDeferredProposal_requiresUserDecision() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "One task deferred",
                List.of(item(null)),
                List.of(new DailyPlanAiResponse.AiAdjustmentProposalDto(
                        null,
                        "Large unfinished task",
                        AiAdjustmentAction.RESCHEDULE,
                        "It does not fit today's available time.",
                        60)));

        var result = evaluator.evaluate(response, context());

        assertThat(result.requiresUserDecision()).isTrue();
    }

    @Test
    void evaluate_withCarryOverItem_requiresUserDecision() {
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Carry-over proposed",
                List.of(item(AiAdjustmentAction.CARRY_OVER)),
                List.of());

        var result = evaluator.evaluate(response, context());

        assertThat(result.requiresUserDecision()).isTrue();
    }

    private DailyPlanAiResponse.AiPlanItemDto item(AiAdjustmentAction action) {
        return new DailyPlanAiResponse.AiPlanItemDto(
                null,
                "Practice",
                null,
                DailyTaskCategory.PRACTICE,
                30,
                action,
                action == null ? null : "Continue important unfinished work.");
    }

    private DailyPlanningContext context() {
        return new DailyPlanningContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "UTC",
                60,
                new DailyPlanningContext.RoadmapContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "Roadmap",
                        null,
                        List.of()),
                List.of(),
                List.of(),
                List.of(),
                null);
    }
}
