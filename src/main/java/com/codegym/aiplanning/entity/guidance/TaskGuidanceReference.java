package com.codegym.aiplanning.entity.guidance;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_guidance_references")
public class TaskGuidanceReference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_guidance_revision_id", nullable = false)
    private TaskGuidanceRevision revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_step_guidance_id")
    private TaskStepGuidance taskStepGuidance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GuidanceReferenceProvenance provenance;

    @Column(name = "display_label", nullable = false, length = 500)
    private String displayLabel;

    @Column(length = 1000)
    private String locator;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "learning_source_id")
    private UUID learningSourceId;

    @Column(name = "roadmap_item_id")
    private UUID roadmapItemId;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    protected TaskGuidanceReference() {}

    public static TaskGuidanceReference material(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance,
            UUID materialId,
            String displayLabel,
            String locator,
            Integer orderIndex) {
        TaskGuidanceReference reference = base(
                revision,
                taskStepGuidance,
                GuidanceReferenceProvenance.MATERIAL,
                displayLabel,
                locator,
                orderIndex);
        reference.materialId = Objects.requireNonNull(
                materialId,
                "materialId must not be null");
        return reference;
    }

    public static TaskGuidanceReference learningSource(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance,
            UUID learningSourceId,
            String displayLabel,
            String locator,
            Integer orderIndex) {
        TaskGuidanceReference reference = base(
                revision,
                taskStepGuidance,
                GuidanceReferenceProvenance.LEARNING_SOURCE,
                displayLabel,
                locator,
                orderIndex);
        reference.learningSourceId = Objects.requireNonNull(
                learningSourceId,
                "learningSourceId must not be null");
        return reference;
    }

    public static TaskGuidanceReference roadmapContext(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance,
            UUID roadmapItemId,
            String displayLabel,
            String locator,
            Integer orderIndex) {
        TaskGuidanceReference reference = base(
                revision,
                taskStepGuidance,
                GuidanceReferenceProvenance.ROADMAP_CONTEXT,
                displayLabel,
                locator,
                orderIndex);
        reference.roadmapItemId = Objects.requireNonNull(
                roadmapItemId,
                "roadmapItemId must not be null");
        return reference;
    }

    public static TaskGuidanceReference unverifiedExternal(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance,
            String externalUrl,
            String displayLabel,
            Integer orderIndex) {
        TaskGuidanceReference reference = base(
                revision,
                taskStepGuidance,
                GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL,
                displayLabel,
                null,
                orderIndex);
        reference.externalUrl = requireText(externalUrl, "externalUrl");
        return reference;
    }

    public boolean isUnverified() {
        return provenance == GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL;
    }

    public TaskGuidanceRevision getRevision() {
        return revision;
    }

    public TaskStepGuidance getTaskStepGuidance() {
        return taskStepGuidance;
    }

    public GuidanceReferenceProvenance getProvenance() {
        return provenance;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getLocator() {
        return locator;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public UUID getLearningSourceId() {
        return learningSourceId;
    }

    public UUID getRoadmapItemId() {
        return roadmapItemId;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    private static TaskGuidanceReference base(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance,
            GuidanceReferenceProvenance provenance,
            String displayLabel,
            String locator,
            Integer orderIndex) {
        requireMatchingRevision(revision, taskStepGuidance);
        TaskGuidanceReference reference = new TaskGuidanceReference();
        reference.revision = Objects.requireNonNull(
                revision,
                "revision must not be null");
        reference.taskStepGuidance = taskStepGuidance;
        reference.provenance = provenance;
        reference.displayLabel = requireText(displayLabel, "displayLabel");
        reference.locator = normalizeOptionalText(locator);
        if (orderIndex == null || orderIndex < 0) {
            throw new IllegalArgumentException("orderIndex must not be negative");
        }
        reference.orderIndex = orderIndex;
        return reference;
    }

    private static void requireMatchingRevision(
            TaskGuidanceRevision revision,
            TaskStepGuidance taskStepGuidance) {
        if (revision == null || taskStepGuidance == null) {
            return;
        }
        if (taskStepGuidance.getRevision() != revision) {
            throw new IllegalArgumentException(
                    "A step-level reference must use the same revision as its Task Step Guidance.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
