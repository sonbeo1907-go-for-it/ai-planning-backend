package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiProviderConfigResponse(
        UUID id,
        long version,
        UUID providerId,
        String providerCode,
        String providerDisplayName,
        AiProviderProtocol protocol,
        String baseUrl,
        AiPurpose purpose,
        String model,
        boolean enabled,
        boolean defaultProvider,
        int timeoutSeconds,
        int maxInputTokens,
        int maxOutputTokens,
        BigDecimal temperature,
        Instant createdAt,
        Instant updatedAt) {}
