package com.codegym.aiplanning.service.daily.ai;

import java.util.List;
import java.util.UUID;

/**
 * Raw AI response model for one task suggestion (US-TSK-AI).
 */
public record TaskSuggestionAiResponse(
        String shortDescription,
        List<ChecklistStepDto> steps,
        List<ReferenceDto> references) {

    public record ChecklistStepDto(String content) {}

    public record ReferenceDto(
            String title,
            String referenceType,
            UUID documentId,
            String url) {}
}
