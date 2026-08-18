package com.codegym.aiplanning.service.ai;

public interface AiClientService {
    /**
     * Generate content from AI provider given a system prompt and a user prompt.
     * @param systemPrompt System level instruction / formatting rules.
     * @param userPrompt Contextual user input / material details.
     * @return Raw string output from AI model (usually JSON).
     */
    String generateContent(String systemPrompt, String userPrompt);
}
