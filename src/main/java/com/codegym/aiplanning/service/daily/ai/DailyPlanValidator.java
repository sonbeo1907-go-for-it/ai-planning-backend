package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.service.ai.AiServiceException;
import org.springframework.stereotype.Component;

@Component
public class DailyPlanValidator {
    
    public void validateResponse(DailyPlanAiResponse response, DailyPlanningContext context) {
        if (response == null || response.items() == null) {
            throw new AiServiceException("AI Response is null or missing items");
        }
        
        for (DailyPlanAiResponse.AiPlanItemDto item : response.items()) {
            // roadmapItemId can be null for CUSTOM or general tasks
            if (item.plannedMinutes() == null || item.plannedMinutes() < 0) {
                throw new AiServiceException("Negative or null minutes for task: " + item.title());
            }
            if (item.category() == null) {
                throw new AiServiceException("Missing category for task: " + item.title());
            }
        }
    }
}
