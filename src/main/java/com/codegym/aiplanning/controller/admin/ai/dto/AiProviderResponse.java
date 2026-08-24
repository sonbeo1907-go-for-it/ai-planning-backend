package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiProviderResponse(
        UUID id,
        long version,
        String code,
        String displayName,
        String baseUrl,
        AiProviderProtocol protocol,
        CredentialSelectionStrategy credentialStrategy,
        boolean enabled,
        List<AiProviderCredentialResponse> credentials,
        Instant createdAt,
        Instant updatedAt) {}
