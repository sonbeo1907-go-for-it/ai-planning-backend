package com.codegym.aiplanning.service.guidance.model;

import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import java.util.List;
import java.util.UUID;

public record GeneratedTaskGuidance(
        UUID dailyPlanVersionId,
        UUID dailyPlanItemId,
        String objective,
        String taskSummary,
        List<GeneratedStepGuidance> stepGuidances,
        List<GeneratedReference> references) {

    public GeneratedTaskGuidance {
        stepGuidances = List.copyOf(stepGuidances);
        references = List.copyOf(references);
    }

    public record GeneratedStepGuidance(
            UUID taskStepId,
            String instructions,
            String expectedResult,
            String tips,
            String cautions,
            String prerequisites,
            List<GeneratedReference> references) {

        public GeneratedStepGuidance {
            references = List.copyOf(references);
        }
    }

    public record GeneratedReference(
            GuidanceReferenceProvenance provenance,
            String displayLabel,
            String locator,
            UUID targetId,
            String externalUrl) {}
}
