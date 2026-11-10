package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiExecutionOperation;

public record AiExecutionAnalyticsResponse(
        String providerDisplayName,
        String model,
        AiExecutionOperation operation,
        Long totalExecutions,
        Long succeededCount,
        Long failedCount,
        Long timeoutCount,
        Double avgLatencyMs,
        Long totalInputTokens,
        Long totalOutputTokens,
        Long totalTokens
) {
    public AiExecutionAnalyticsResponse(
            String providerDisplayName,
            String model,
            AiExecutionOperation operation,
            Long totalExecutions,
            Long succeededCount,
            Long failedCount,
            Long timeoutCount,
            Double avgLatencyMs,
            Long totalInputTokens,
            Long totalOutputTokens) {
        this(
                providerDisplayName,
                model,
                operation,
                totalExecutions != null ? totalExecutions : 0L,
                succeededCount != null ? succeededCount : 0L,
                failedCount != null ? failedCount : 0L,
                timeoutCount != null ? timeoutCount : 0L,
                avgLatencyMs,
                totalInputTokens,
                totalOutputTokens,
                (totalInputTokens != null ? totalInputTokens : 0L) + (totalOutputTokens != null ? totalOutputTokens : 0L)
        );
    }
}
