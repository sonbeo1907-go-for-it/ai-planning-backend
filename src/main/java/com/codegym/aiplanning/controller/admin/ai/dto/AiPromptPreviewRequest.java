package com.codegym.aiplanning.controller.admin.ai.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record AiPromptPreviewRequest(
        @NotNull AiPurpose purpose,
        @NotBlank @Size(max = 20000) String content,
        Map<String, String> syntheticData
) {}
