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

    AiExecutionResponse submitDailyPlanGeneration(
            UUID ownerId, UUID dailyPlanId, String idempotencyKey);

    AiExecutionResponse submitDailyPlanRegeneration(
            UUID ownerId, UUID dailyPlanId, String idempotencyKey);

    AiExecutionResponse getLatestDailyPlanExecution(
            UUID ownerId, UUID dailyPlanId);

    AiExecutionResponse submitDailyQuizGeneration(
            UUID ownerId,
            UUID dailyPlanId,
            String idempotencyKey);

    AiExecutionResponse getLatestDailyQuizExecution(
            UUID ownerId,
            UUID dailyPlanId);

    AiExecutionResponse submitMasteryCheckGeneration(
            UUID ownerId,
            UUID weakTopicId,
            String idempotencyKey);
}
