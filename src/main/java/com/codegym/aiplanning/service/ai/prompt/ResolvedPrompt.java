package com.codegym.aiplanning.service.ai.prompt;

import com.codegym.aiplanning.entity.ai.AiPromptSource;
import java.util.UUID;

public record ResolvedPrompt(
        UUID versionId,
        AiPromptSource source,
        String content
) {
    public boolean isDbVersion() {
        return source == AiPromptSource.DB_VERSION && versionId != null;
    }
}
