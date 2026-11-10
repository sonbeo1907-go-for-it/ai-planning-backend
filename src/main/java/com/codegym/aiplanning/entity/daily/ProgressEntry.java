package com.codegym.aiplanning.entity.daily;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "progress_entries")
@EntityListeners(AuditingEntityListener.class)
public class ProgressEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "daily_plan_item_id")
    private UUID dailyPlanItemId;

    @Column(name = "roadmap_version_id")
    private UUID roadmapVersionId;

    @Column(name = "learning_unit_id")
    private UUID learningUnitId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProgressEntryStatus status;

    @Column(name = "actual_minutes", nullable = false)
    private Integer actualMinutes;

    @Column(columnDefinition = "TEXT", name = "actual_result")
    private String actualResult;

    @Column(name = "difficulty")
    private Integer difficulty;

    @Column(name = "understanding_rating")
    private Integer understandingRating;

    @Column(columnDefinition = "TEXT", name = "note")
    private String note;

    @Column(name = "supersedes_entry_id")
    private UUID supersedesEntryId;

    @Column(name = "completion_percentage", nullable = false)
    private Integer completionPercentage;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ProgressEntry() {}

    public static ProgressEntry create(
            UUID userId,
            UUID dailyPlanItemId,
            ProgressEntryStatus status,
            Integer actualMinutes,
            Integer completionPercentage,
            String actualResult,
            Integer difficulty,
            Integer understandingRating,
            String note,
            UUID supersedesEntryId) {
        return create(
                userId,
                dailyPlanItemId,
                null,
                null,
                status,
                actualMinutes,
                completionPercentage,
                actualResult,
                difficulty,
                understandingRating,
                note,
                supersedesEntryId,
                null);
    }

    public static ProgressEntry create(
            UUID userId,
            UUID dailyPlanItemId,
            UUID roadmapVersionId,
            UUID learningUnitId,
            ProgressEntryStatus status,
            Integer actualMinutes,
            Integer completionPercentage,
            String actualResult,
            Integer difficulty,
            Integer understandingRating,
            String note,
            UUID supersedesEntryId,
            String idempotencyKey) {
        ProgressEntry entry = new ProgressEntry();
        entry.userId = userId;
        entry.dailyPlanItemId = dailyPlanItemId;
        entry.roadmapVersionId = roadmapVersionId;
        entry.learningUnitId = learningUnitId;
        entry.idempotencyKey = idempotencyKey;
        entry.status = status;
        entry.actualMinutes = actualMinutes != null ? actualMinutes : 0;
        entry.completionPercentage = completionPercentage != null ? completionPercentage : (status == ProgressEntryStatus.COMPLETED ? 100 : 0);
        entry.actualResult = actualResult;
        entry.difficulty = difficulty;
        entry.understandingRating = understandingRating;
        entry.note = note;
        entry.supersedesEntryId = supersedesEntryId;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDailyPlanItemId() {
        return dailyPlanItemId;
    }

    public UUID getRoadmapVersionId() {
        return roadmapVersionId;
    }

    public UUID getLearningUnitId() {
        return learningUnitId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Integer getActualMinutes() {
        return actualMinutes;
    }

    public Integer getCompletionPercentage() {
        return completionPercentage;
    }

    public ProgressEntryStatus getStatus() {
        return status;
    }

    public String getActualResult() {
        return actualResult;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public Integer getUnderstandingRating() {
        return understandingRating;
    }

    public String getNote() {
        return note;
    }

    public UUID getSupersedesEntryId() {
        return supersedesEntryId;
    }
}
