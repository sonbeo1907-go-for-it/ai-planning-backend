package com.codegym.aiplanning.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
        @PositiveOrZero long welcomeCredits) {}
