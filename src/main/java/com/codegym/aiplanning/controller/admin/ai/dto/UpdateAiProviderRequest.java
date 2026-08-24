package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAiProviderRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotNull AiProviderProtocol protocol,
        @NotNull CredentialSelectionStrategy credentialStrategy) {}
