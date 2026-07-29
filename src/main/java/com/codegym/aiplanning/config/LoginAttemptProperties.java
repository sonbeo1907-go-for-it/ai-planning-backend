package com.codegym.aiplanning.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.login-attempts")
public record LoginAttemptProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration blockDuration) {

    @AssertTrue(message = "block duration must be greater than zero")
    public boolean isBlockDurationPositive() {
        return blockDuration != null && !blockDuration.isZero() && !blockDuration.isNegative();
    }
}
