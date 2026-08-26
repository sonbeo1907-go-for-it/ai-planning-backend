package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import java.util.List;
import java.util.UUID;

/**
 * Validated and normalized task suggestion ready for persistence (US-TSK-AI).
 */
public record ValidatedTaskSuggestion(
        String shortDescription,
        List<String> steps,
        List<ValidatedReference> references) {

    public record ValidatedReference(
            TaskAiReferenceType type,
            String title,
            String url,
            UUID documentId,
            boolean verified) {}
}
