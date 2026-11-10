package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.time.Instant;
import java.util.UUID;

public record AdminAiExecutionListResponse(
        UUID id,
        UUID providerId,
        String providerName,
        String model,
        AiPurpose purpose,
        AiExecutionOperation operation,
        AiExecutionStatus status,
        String failureCode,
        Instant createdAt
) {}
