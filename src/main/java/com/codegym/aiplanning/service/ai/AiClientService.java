package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;

public interface AiClientService {

    String generateContent(AiPurpose purpose, String systemPrompt, String userPrompt);

    String generateContent(
            AiProviderConfig providerConfig, String systemPrompt, String userPrompt);
}
