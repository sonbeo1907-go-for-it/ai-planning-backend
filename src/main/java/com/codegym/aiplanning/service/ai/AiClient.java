package com.codegym.aiplanning.service.ai;

public interface AiClient {
    /**
     * Sends prompts to the AI provider and returns the raw JSON response.
     */
    String generate(String systemPrompt, String userPrompt);
}
