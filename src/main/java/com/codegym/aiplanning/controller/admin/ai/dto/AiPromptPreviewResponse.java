package com.codegym.aiplanning.controller.admin.ai.dto;

import java.util.Map;

public record AiPromptPreviewResponse(
        String renderedContent,
        Map<String, String> sampleData
) {}
