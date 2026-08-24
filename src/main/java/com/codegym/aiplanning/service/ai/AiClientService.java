package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.entity.ai.AiPurpose;

public interface AiClientService {

    String generateContent(AiPurpose purpose, String systemPrompt, String userPrompt);
}
