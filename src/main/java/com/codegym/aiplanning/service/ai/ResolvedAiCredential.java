package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.entity.ai.AiProviderCredential;

public record ResolvedAiCredential(AiProviderCredential credential, String secret) {}
