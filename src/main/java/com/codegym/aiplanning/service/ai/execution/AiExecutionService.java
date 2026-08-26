package com.codegym.aiplanning.service.ai.execution;

import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import java.util.List;
import java.util.UUID;

public interface AiExecutionService {

    AiExecutionResponse submitRoadmapGeneration(
            UUID ownerId,
            UUID roadmapId,
            List<UUID> materialIds,
            String idempotencyKey);

    AiExecutionResponse submitRoadmapRegeneration(
            UUID ownerId,
            UUID roadmapId,
            String adjustmentPrompt,
            String idempotencyKey);

    AiExecutionResponse getOwnedExecution(UUID ownerId, UUID executionId);

    AiExecutionResponse getLatestRoadmapExecution(UUID ownerId, UUID roadmapId);
}
