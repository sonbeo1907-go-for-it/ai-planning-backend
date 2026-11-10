package com.codegym.aiplanning.controller.admin.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiProviderCredentialResponse(
        UUID id,
        long version,
        UUID providerId,
        String label,
        String secretRef,
        int priority,
        boolean enabled,
        boolean secretConfigured,
        String maskedSecret,
        Instant createdAt,
        Instant updatedAt) {}
