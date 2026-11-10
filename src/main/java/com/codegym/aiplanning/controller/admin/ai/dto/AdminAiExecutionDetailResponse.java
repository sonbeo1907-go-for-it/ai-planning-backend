package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAiExecutionDetailResponse(
        UUID id,
        UUID providerId,
        String providerName,
        String model,
        AiPurpose purpose,
        AiExecutionOperation operation,
        AiExecutionStatus status,
        int attemptCount,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        String failureCode,
        String failureMessage,
        List<AdminAiExecutionTimelineEvent> timeline
) {}
