package com.codegym.aiplanning.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.session")
public record AuthSessionProperties(
        @NotNull Duration absoluteExpiration,
        @NotNull Duration refreshLockDuration,
        @NotBlank String refreshCookieName,
        @NotBlank String refreshCookiePath,
        boolean refreshCookieSecure,
        @NotBlank String refreshCookieSameSite,
        boolean redisEnabled) {}
