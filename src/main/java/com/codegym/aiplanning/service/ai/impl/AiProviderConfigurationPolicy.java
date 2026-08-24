package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.AiProviderProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AiProviderConfigurationPolicy {

    private static final String PROVIDER_CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,49}$";
    private static final String SECRET_REF_PATTERN = "^env:[A-Z][A-Z0-9_]{2,127}$";

    private final AiProviderProperties properties;

    public AiProviderConfigurationPolicy(AiProviderProperties properties) {
        this.properties = properties;
    }

    public String normalizeProviderCode(String code) {
        String normalized = normalizeRequired(code, "Provider code is required.")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches(PROVIDER_CODE_PATTERN)) {
            throw invalid(
                    "Provider code must contain only uppercase letters, numbers, and underscores.");
        }
        return normalized;
    }

    public String normalizeDisplayName(String displayName) {
        return normalizeRequired(displayName, "Provider display name is required.");
    }

    public String normalizeCredentialLabel(String label) {
        return normalizeRequired(label, "Credential label is required.");
    }

    public String normalizeModel(String model) {
        return normalizeRequired(model, "Model is required.");
    }

    public String normalizeSecretRef(String secretRef) {
        String normalized = normalizeRequired(secretRef, "Secret reference is required.");
        if (!normalized.matches(SECRET_REF_PATTERN)) {
            throw invalid("Secret reference must use the env:VARIABLE_NAME format.");
        }
        return normalized;
    }

    public String validateAndNormalizeBaseUrl(String baseUrl) {
        String normalized = normalizeRequired(baseUrl, "Provider base URL is required.");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        URI uri = parse(normalized);
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw invalid(
                    "Provider base URL must be an absolute URL without credentials, query, or fragment.");
        }

        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean testHttp = properties.allowInsecureHttpBaseUrls()
                && "http".equalsIgnoreCase(uri.getScheme());
        if (!https && !testHttp) {
            throw invalid("Provider base URL must use HTTPS.");
        }
        return normalized;
    }

    private URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid("Provider base URL is invalid.");
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AI_PROVIDER_INVALID_CONFIGURATION, message);
    }
}
