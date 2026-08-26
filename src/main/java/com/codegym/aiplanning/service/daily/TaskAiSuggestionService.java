package com.codegym.aiplanning.service.daily;

import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemAiSuggestionResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemDetailResponse;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Owner-scoped AI task suggestion service (US-TSK-AI).
 */
public interface TaskAiSuggestionService {

    DailyPlanItemDetailResponse getTaskDetail(UUID planId, UUID itemId, Jwt actorJwt);

    DailyPlanItemAiSuggestionResponse generateSuggestion(
            UUID planId, UUID itemId, String idempotencyKey, Jwt actorJwt);

    DailyPlanItemAiSuggestionResponse regenerateSuggestion(UUID planId, UUID itemId, Jwt actorJwt);
}
