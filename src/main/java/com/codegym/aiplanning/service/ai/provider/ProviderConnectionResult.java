package com.codegym.aiplanning.service.ai.provider;

public record ProviderConnectionResult(
        boolean success,
        long latencyMs,
        String failureCategory,
        String message) {

    public static ProviderConnectionResult success(long latencyMs) {
        return new ProviderConnectionResult(
                true, latencyMs, null, "Connection succeeded.");
    }

    public static ProviderConnectionResult failure(
            long latencyMs, String failureCategory, String message) {
        return new ProviderConnectionResult(false, latencyMs, failureCategory, message);
    }
}
