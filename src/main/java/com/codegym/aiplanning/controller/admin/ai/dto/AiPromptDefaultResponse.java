package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;

public record AiPromptDefaultResponse(
        AiPurpose purpose,
        String content
) {
}
