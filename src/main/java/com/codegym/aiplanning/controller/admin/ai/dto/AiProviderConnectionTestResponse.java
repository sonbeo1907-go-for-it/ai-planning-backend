package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.time.Instant;
import java.util.UUID;

public record AiProviderConnectionTestResponse(
        UUID configId,
        UUID providerId,
        String providerCode,
        AiPurpose purpose,
        String model,
        UUID credentialId,
        String credentialLabel,
        boolean success,
        long latencyMs,
        String failureCategory,
        String message,
        Instant testedAt) {}
