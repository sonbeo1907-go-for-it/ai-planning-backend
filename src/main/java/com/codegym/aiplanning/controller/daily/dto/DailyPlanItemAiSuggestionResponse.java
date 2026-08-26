package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestion;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionReference;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionStep;
import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "AI suggestion details for one daily task item (US-TSK-AI)")
public record DailyPlanItemAiSuggestionResponse(
        UUID id,
        String shortDescription,
        List<ChecklistStepResponse> steps,
        List<ReferenceResponse> references) {

    @Schema(description = "One ordered action-checklist step")
    public record ChecklistStepResponse(Integer orderIndex, String content) {}

    @Schema(description = "Reference document or link recommended by AI")
    public record ReferenceResponse(
            TaskAiReferenceType referenceType,
            String title,
            String url,
            UUID documentId,
            boolean verified) {}

    public static DailyPlanItemAiSuggestionResponse from(
            DailyPlanItemAiSuggestion suggestion,
            List<DailyPlanItemAiSuggestionStep> steps,
            List<DailyPlanItemAiSuggestionReference> references) {
        return new DailyPlanItemAiSuggestionResponse(
                suggestion.getId(),
                suggestion.getShortDescription(),
                steps.stream()
                        .map(step -> new ChecklistStepResponse(
                                step.getOrderIndex(), step.getContent()))
                        .toList(),
                references.stream()
                        .map(reference -> new ReferenceResponse(
                                reference.getReferenceType(),
                                reference.getTitle(),
                                reference.getUrl(),
                                reference.getDocumentId(),
                                reference.isVerified()))
                        .toList());
    }
}
