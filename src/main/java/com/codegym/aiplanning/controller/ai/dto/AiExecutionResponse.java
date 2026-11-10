package com.codegym.aiplanning.controller.ai.dto;

import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionResultType;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.time.Instant;
import java.util.UUID;

public record AiExecutionResponse(
        UUID id,
        long entityVersion,
        UUID providerConfigId,
        AiPurpose purpose,
        AiExecutionOperation operation,
        AiExecutionTargetType targetType,
        UUID targetId,
        AiExecutionStatus status,
        AiExecutionResultType resultType,
        UUID resultId,
        int attemptCount,
        String failureCode,
        String failureMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AiExecutionResponse from(AiExecution execution) {
        return new AiExecutionResponse(
                execution.getId(),
                execution.getVersion(),
                execution.getProviderConfig().getId(),
                execution.getPurpose(),
                execution.getOperation(),
                execution.getTargetType(),
                execution.getTargetId(),
                execution.getStatus(),
                execution.getResultType(),
                execution.getResultId(),
                execution.getAttemptCount(),
                execution.getFailureCode(),
                execution.getFailureMessage(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getCreatedAt(),
                execution.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "AiExecutionResponse[id=" + id
                + ", purpose=" + purpose
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}
