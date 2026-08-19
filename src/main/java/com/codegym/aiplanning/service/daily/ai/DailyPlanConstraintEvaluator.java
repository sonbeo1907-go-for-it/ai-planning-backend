package com.codegym.aiplanning.service.daily.ai;

import org.springframework.stereotype.Component;

@Component
public class DailyPlanConstraintEvaluator {
    
    public record EvaluationResult(boolean requiresUserDecision) {}

    public EvaluationResult evaluate(DailyPlanAiResponse response, DailyPlanningContext context) {
        int totalMinutes = 0;
        boolean hasAiProposals = false;

        if (response.items() != null) {
            for (DailyPlanAiResponse.AiPlanItemDto item : response.items()) {
                totalMinutes += item.plannedMinutes() != null ? item.plannedMinutes() : 0;
                
                // If AI proposes a split, reschedule, or drop, the user should be prompted to decide/verify
                if (item.aiAdjustmentAction() != null) {
                    hasAiProposals = true;
                }
            }
        }
        
        boolean requiresUserDecision = (totalMinutes > context.availableMinutes()) || hasAiProposals;
        return new EvaluationResult(requiresUserDecision);
    }
}
