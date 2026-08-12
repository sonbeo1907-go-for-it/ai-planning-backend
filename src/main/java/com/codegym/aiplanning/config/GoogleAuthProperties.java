package com.codegym.aiplanning.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.google")
public record GoogleAuthProperties(
        boolean enabled,
        String clientId,
        @NotBlank String issuer,
        @NotBlank String jwkSetUri) {}
