package com.codegym.aiplanning.service.ai.provider;

import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;

public interface AiProviderConnectionTester {

    AiProviderProtocol protocol();

    ProviderConnectionResult test(
            AiProvider provider, AiProviderConfig config, String apiKey);
}
