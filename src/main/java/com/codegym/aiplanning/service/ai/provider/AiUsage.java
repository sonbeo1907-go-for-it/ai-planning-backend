package com.codegym.aiplanning.service.ai.provider;

public record AiUsage(Integer inputTokens, Integer outputTokens) {

    public Integer totalTokens() {
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        return (inputTokens != null ? inputTokens : 0) + (outputTokens != null ? outputTokens : 0);
    }
}
