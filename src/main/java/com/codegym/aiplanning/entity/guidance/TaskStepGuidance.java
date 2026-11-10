package com.codegym.aiplanning.entity.guidance;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_step_guidances")
public class TaskStepGuidance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_guidance_revision_id", nullable = false)
    private TaskGuidanceRevision revision;

    @Column(name = "source_task_step_id", nullable = false)
    private UUID sourceTaskStepId;

    @Column(name = "task_step_entity_version", nullable = false)
    private Long taskStepEntityVersion;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    @Column(columnDefinition = "TEXT")
    private String tips;

    @Column(columnDefinition = "TEXT")
    private String cautions;

    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    protected TaskStepGuidance() {}

    public static TaskStepGuidance create(
            TaskGuidanceRevision revision,
            UUID sourceTaskStepId,
            Long taskStepEntityVersion,
            Integer orderIndex,
            String instructions,
            String expectedResult,
            String tips,
            String cautions,
            String prerequisites) {
        TaskStepGuidance stepGuidance = new TaskStepGuidance();
        stepGuidance.revision = Objects.requireNonNull(
                revision,
                "revision must not be null");
        stepGuidance.sourceTaskStepId = Objects.requireNonNull(
                sourceTaskStepId,
                "sourceTaskStepId must not be null");
        stepGuidance.taskStepEntityVersion = requireNonNegative(
                taskStepEntityVersion,
                "taskStepEntityVersion");
        stepGuidance.orderIndex = requireNonNegative(orderIndex, "orderIndex");
        stepGuidance.instructions = requireText(instructions, "instructions");
        stepGuidance.expectedResult = requireText(expectedResult, "expectedResult");
        stepGuidance.tips = normalizeOptionalText(tips);
        stepGuidance.cautions = normalizeOptionalText(cautions);
        stepGuidance.prerequisites = normalizeOptionalText(prerequisites);
        return stepGuidance;
    }

    public TaskGuidanceRevision getRevision() {
        return revision;
    }

    public UUID getSourceTaskStepId() {
        return sourceTaskStepId;
    }

    public Long getTaskStepEntityVersion() {
        return taskStepEntityVersion;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getTips() {
        return tips;
    }

    public String getCautions() {
        return cautions;
    }

    public String getPrerequisites() {
        return prerequisites;
    }

    private static <T extends Number> T requireNonNegative(T value, String fieldName) {
        if (value == null || value.longValue() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
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
