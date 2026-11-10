package com.codegym.aiplanning.controller.admin.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateAiProviderConfigRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 150) String model,
        @Min(1) @Max(120) int timeoutSeconds,
        @Min(1) int maxInputTokens,
        @Min(1) int maxOutputTokens,
        @NotNull @DecimalMin("0.00") @DecimalMax("2.00") BigDecimal temperature,
        @Size(max = 50) @jakarta.validation.constraints.Pattern(regexp = "\\s*([A-Za-z0-9_-]{1,50})?\\s*") String pricingCategory) {
    public UpdateAiProviderConfigRequest(Long version, String model, int timeoutSeconds,
            int maxInputTokens, int maxOutputTokens, BigDecimal temperature) {
        this(version, model, timeoutSeconds, maxInputTokens, maxOutputTokens, temperature, null);
    }
}
