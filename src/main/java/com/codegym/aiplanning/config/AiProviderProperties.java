package com.codegym.aiplanning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProviderProperties(boolean allowInsecureHttpBaseUrls) {}
