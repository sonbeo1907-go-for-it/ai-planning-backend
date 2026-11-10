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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "task_guidance_revisions")
public class TaskGuidanceRevision extends BaseEntity {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_guidance_id", nullable = false)
    private TaskGuidance taskGuidance;

    @Column(name = "ai_execution_id", nullable = false, unique = true)
    private UUID aiExecutionId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskGuidanceRevisionStatus status;

    @Column(nullable = false, length = 500)
    private String objective;

    @Column(name = "task_summary", nullable = false, columnDefinition = "TEXT")
    private String taskSummary;

    @Column(name = "daily_plan_item_entity_version", nullable = false)
    private Long dailyPlanItemEntityVersion;

    @Column(name = "context_fingerprint", nullable = false, length = 64)
    private String contextFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_slot_guidance_id")
    private TaskGuidance draftSlotGuidance;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected TaskGuidanceRevision() {}

    public static TaskGuidanceRevision draft(
            TaskGuidance taskGuidance,
            UUID aiExecutionId,
            Integer revisionNumber,
            String objective,
            String taskSummary,
            Long dailyPlanItemEntityVersion,
            String contextFingerprint,
            Instant generatedAt) {
        TaskGuidanceRevision revision = new TaskGuidanceRevision();
        revision.taskGuidance = Objects.requireNonNull(
                taskGuidance,
                "taskGuidance must not be null");
        revision.aiExecutionId = Objects.requireNonNull(
                aiExecutionId,
                "aiExecutionId must not be null");
        revision.revisionNumber = requirePositiveRevisionNumber(revisionNumber);
        revision.status = TaskGuidanceRevisionStatus.DRAFT;
        revision.objective = requireText(objective, "objective");
        revision.taskSummary = requireText(taskSummary, "taskSummary");
        revision.dailyPlanItemEntityVersion = requireNonNegativeVersion(
                dailyPlanItemEntityVersion,
                "dailyPlanItemEntityVersion");
        revision.contextFingerprint = requireFingerprint(contextFingerprint);
        revision.draftSlotGuidance = taskGuidance;
        revision.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        return revision;
    }

    public void supersede() {
        if (status != TaskGuidanceRevisionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a DRAFT Task Guidance revision can be superseded.");
        }
        status = TaskGuidanceRevisionStatus.SUPERSEDED;
        draftSlotGuidance = null;
    }

    public void archive() {
        if (status == TaskGuidanceRevisionStatus.ARCHIVED) {
            return;
        }
        status = TaskGuidanceRevisionStatus.ARCHIVED;
        draftSlotGuidance = null;
    }

    public boolean isDraft() {
        return status == TaskGuidanceRevisionStatus.DRAFT;
    }

    public TaskGuidance getTaskGuidance() {
        return taskGuidance;
    }

    public UUID getAiExecutionId() {
        return aiExecutionId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public TaskGuidanceRevisionStatus getStatus() {
        return status;
    }

    public String getObjective() {
        return objective;
    }

    public String getTaskSummary() {
        return taskSummary;
    }

    public Long getDailyPlanItemEntityVersion() {
        return dailyPlanItemEntityVersion;
    }

    public String getContextFingerprint() {
        return contextFingerprint;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    private static Integer requirePositiveRevisionNumber(Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("revisionNumber must be positive");
        }
        return value;
    }

    private static Long requireNonNegativeVersion(Long value, String fieldName) {
        if (value == null || value < 0) {
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

    private static String requireFingerprint(String value) {
        if (value == null || !SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "contextFingerprint must be a lowercase SHA-256 value");
        }
        return value;
    }
}
