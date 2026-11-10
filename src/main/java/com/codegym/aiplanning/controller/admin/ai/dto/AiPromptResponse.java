package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiPromptStatus;
import com.codegym.aiplanning.entity.ai.AiPromptTemplate;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.time.Instant;
import java.util.UUID;

public record AiPromptResponse(
        UUID id,
        AiPurpose purpose,
        int versionNumber,
        String content,
        AiPromptStatus status,
        boolean isActive,
        boolean isSystem,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiPromptResponse from(AiPromptTemplate template) {
        return new AiPromptResponse(
                template.getId(),
                template.getPurpose(),
                template.getVersionNumber(),
                template.getContent(),
                template.getStatus(),
                template.isActive(),
                template.isSystem(),
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
