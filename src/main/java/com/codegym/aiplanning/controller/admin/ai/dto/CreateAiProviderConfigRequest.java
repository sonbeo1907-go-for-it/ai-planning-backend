package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAiProviderConfigRequest(
        @NotNull UUID providerId,
        @NotNull AiPurpose purpose,
        @NotBlank @Size(max = 150) String model,
        boolean enabled,
        boolean defaultProvider,
        @Min(1) @Max(120) int timeoutSeconds,
        @Min(1) int maxInputTokens,
        @Min(1) int maxOutputTokens,
        @NotNull @DecimalMin("0.00") @DecimalMax("2.00") BigDecimal temperature) {}
