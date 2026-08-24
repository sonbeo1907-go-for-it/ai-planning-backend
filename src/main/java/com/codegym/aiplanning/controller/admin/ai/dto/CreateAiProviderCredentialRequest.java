package com.codegym.aiplanning.controller.admin.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAiProviderCredentialRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank
                @Size(max = 150)
                @Pattern(
                        regexp = "^env:[A-Z][A-Z0-9_]{2,127}$",
                        message = "must use the env:VARIABLE_NAME format")
                String secretRef,
        @Min(0) int priority,
        boolean enabled) {}
