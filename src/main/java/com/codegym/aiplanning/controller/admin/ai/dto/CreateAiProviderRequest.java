package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAiProviderRequest(
        @NotBlank
                @Size(max = 50)
                @Pattern(
                        regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
                        message = "must contain only letters, numbers, and underscores")
                String code,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotNull AiProviderProtocol protocol,
        @NotNull CredentialSelectionStrategy credentialStrategy,
        boolean enabled,
        @Valid InitialAiProviderCredentialRequest initialCredential) {}
