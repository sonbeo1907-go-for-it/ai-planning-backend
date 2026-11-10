package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiPurpose;

public interface AiProviderSelector {

    AiProviderConfig requireDefault(AiPurpose purpose);
}
