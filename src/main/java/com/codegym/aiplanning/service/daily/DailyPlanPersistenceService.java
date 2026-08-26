package com.codegym.aiplanning.service.daily;

import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import java.util.List;
import java.util.UUID;

public interface DailyPlanPersistenceService {

    /**
     * Persists the AI-generated draft inside a single transaction boundary.
     * It handles superseding any old draft and saving the new version and items.
     */
    DailyPlanVersion persistAiGeneratedDraft(
            UUID planId,
            UUID userId,
            String username,
            int totalPlannedMinutes,
            String aiExplanation,
            boolean requiresUserDecision,
            String generationRequestKey,
            List<DailyPlanAiResponse.AiPlanItemDto> aiItems);
}
