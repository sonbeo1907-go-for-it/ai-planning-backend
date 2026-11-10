package com.codegym.aiplanning.service.guidance.model;

import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record TaskGuidanceContext(
        UUID ownerId,
        UUID dailyPlanId,
        UUID dailyPlanVersionId,
        UUID dailyPlanItemId,
        long dailyPlanItemEntityVersion,
        String locale,
        String category,
        String status,
        String title,
        String description,
        int plannedMinutes,
        List<TaskStepSnapshot> taskSteps,
        RoadmapContext roadmapContext,
        List<SourceContext> sources,
        String fingerprint) {

    public TaskGuidanceContext {
        taskSteps = List.copyOf(taskSteps);
        sources = List.copyOf(sources);
    }

    public Set<UUID> allowedMaterialIds() {
        return sourceIds(GuidanceReferenceProvenance.MATERIAL);
    }

    public Set<UUID> allowedLearningSourceIds() {
        return sourceIds(GuidanceReferenceProvenance.LEARNING_SOURCE);
    }

    public Set<UUID> allowedRoadmapItemIds() {
        return roadmapContext == null
                ? Set.of()
                : Set.of(roadmapContext.learningUnitId());
    }

    private Set<UUID> sourceIds(GuidanceReferenceProvenance provenance) {
        return sources.stream()
                .filter(source -> source.provenance() == provenance)
                .map(SourceContext::sourceId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public record TaskStepSnapshot(
            UUID id,
            long entityVersion,
            int orderIndex,
            String title,
            String plannedGuidance,
            Integer estimatedMinutes,
            boolean required) {}

    public record RoadmapContext(
            UUID roadmapId,
            UUID learningUnitId,
            String learningUnitTitle,
            String learningUnitDescription,
            UUID topicId,
            String topicTitle,
            UUID milestoneId,
            String milestoneTitle) {}

    public record SourceContext(
            GuidanceReferenceProvenance provenance,
            UUID sourceId,
            String label,
            String boundedContent) {}
}
