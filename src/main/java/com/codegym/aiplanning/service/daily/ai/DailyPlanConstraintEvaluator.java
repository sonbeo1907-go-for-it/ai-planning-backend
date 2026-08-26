package com.codegym.aiplanning.service.daily.ai;

import org.springframework.stereotype.Component;

@Component
public class DailyPlanConstraintEvaluator {

    public record EvaluationResult(boolean requiresUserDecision) {}

    public EvaluationResult evaluate(DailyPlanAiResponse response, DailyPlanningContext context) {
        boolean hasAiProposals = false;

        if (response.items() != null) {
            for (DailyPlanAiResponse.AiPlanItemDto item : response.items()) {
                if (item.aiAdjustmentAction() != null) {
                    hasAiProposals = true;
                }
            }
        }
        boolean hasDeferredAdjustments = response.adjustments() != null
                && !response.adjustments().isEmpty();
        return new EvaluationResult(hasAiProposals || hasDeferredAdjustments);
    }
}
