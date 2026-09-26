package com.codegym.aiplanning.controller.admin.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAiPromptDraftRequest(
        @NotBlank @Size(max = 20000) String content,
        @Min(0) long version
) {}
