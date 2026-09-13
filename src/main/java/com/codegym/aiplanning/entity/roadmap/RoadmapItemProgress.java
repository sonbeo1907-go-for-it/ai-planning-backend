package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roadmap_item_progress")
public class RoadmapItemProgress extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "roadmap_version_id", nullable = false)
    private UUID roadmapVersionId;

    @Column(name = "roadmap_item_id", nullable = false)
    private UUID roadmapItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoadmapItemProgressStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "latest_outcome", length = 30)
    private ProgressEntryStatus latestOutcome;

    @Column(name = "completion_percentage", nullable = false)
    private int completionPercentage;

    @Column(name = "last_progress_entry_id")
    private UUID lastProgressEntryId;

    @Column(name = "carried_from_progress_id")
    private UUID carriedFromProgressId;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RoadmapItemProgress() {}

    public static RoadmapItemProgress create(
            UUID userId, UUID roadmapVersionId, UUID roadmapItemId) {
        RoadmapItemProgress progress = new RoadmapItemProgress();
        progress.userId = userId;
        progress.roadmapVersionId = roadmapVersionId;
        progress.roadmapItemId = roadmapItemId;
        progress.status = RoadmapItemProgressStatus.NOT_STARTED;
        progress.completionPercentage = 0;
        return progress;
    }

    public static RoadmapItemProgress carryForward(
            UUID userId,
            UUID roadmapVersionId,
            UUID roadmapItemId,
            RoadmapItemProgress source) {
        RoadmapItemProgress progress = create(
                userId, roadmapVersionId, roadmapItemId);
        progress.status = source.status;
        progress.latestOutcome = source.latestOutcome;
        progress.completionPercentage = source.completionPercentage;
        progress.completedAt = source.completedAt;
        progress.carriedFromProgressId = source.getId();
        return progress;
    }

    public void markInProgress(UUID progressEntryId, int percentage) {
        if (status != RoadmapItemProgressStatus.COMPLETED) {
            status = RoadmapItemProgressStatus.IN_PROGRESS;
            completionPercentage = Math.max(1, Math.min(99, percentage));
            completedAt = null;
        }
        latestOutcome = ProgressEntryStatus.PARTIALLY_COMPLETED;
        lastProgressEntryId = progressEntryId;
    }

    public void recordSkipped(UUID progressEntryId) {
        latestOutcome = ProgressEntryStatus.SKIPPED;
        lastProgressEntryId = progressEntryId;
    }

    public void markCompleted(UUID progressEntryId, Instant completionTime) {
        status = RoadmapItemProgressStatus.COMPLETED;
        completionPercentage = 100;
        completedAt = completedAt == null ? completionTime : completedAt;
        latestOutcome = ProgressEntryStatus.COMPLETED;
        lastProgressEntryId = progressEntryId;
    }

    public void applyCorrection(
            ProgressEntryStatus outcome,
            int percentage,
            UUID progressEntryId,
            Instant completionTime) {
        latestOutcome = outcome;
        lastProgressEntryId = progressEntryId;
        if (outcome == ProgressEntryStatus.COMPLETED) {
            status = RoadmapItemProgressStatus.COMPLETED;
            completionPercentage = 100;
            completedAt = completionTime;
        } else if (outcome == ProgressEntryStatus.PARTIALLY_COMPLETED) {
            status = RoadmapItemProgressStatus.IN_PROGRESS;
            completionPercentage = Math.max(1, Math.min(99, percentage));
            completedAt = null;
        } else {
            status = RoadmapItemProgressStatus.NOT_STARTED;
            completionPercentage = 0;
            completedAt = null;
        }
    }

    public void replaceProjection(
            RoadmapItemProgressStatus status,
            ProgressEntryStatus latestOutcome,
            int completionPercentage,
            UUID lastProgressEntryId,
            Instant completedAt) {
        this.status = status;
        this.latestOutcome = latestOutcome;
        this.completionPercentage = completionPercentage;
        this.lastProgressEntryId = lastProgressEntryId;
        this.completedAt = completedAt;
    }

    public void applyAggregate(int percentage, UUID progressEntryId, Instant completionTime) {
        completionPercentage = Math.max(0, Math.min(100, percentage));
        lastProgressEntryId = progressEntryId;
        if (completionPercentage == 100) {
            status = RoadmapItemProgressStatus.COMPLETED;
            completedAt = completedAt == null ? completionTime : completedAt;
        } else {
            status = completionPercentage == 0
                    ? RoadmapItemProgressStatus.NOT_STARTED
                    : RoadmapItemProgressStatus.IN_PROGRESS;
            completedAt = null;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoadmapVersionId() {
        return roadmapVersionId;
    }

    public UUID getRoadmapItemId() {
        return roadmapItemId;
    }

    public RoadmapItemProgressStatus getStatus() {
        return status;
    }

    public ProgressEntryStatus getLatestOutcome() {
        return latestOutcome;
    }

    public int getCompletionPercentage() {
        return completionPercentage;
    }

    public UUID getLastProgressEntryId() {
        return lastProgressEntryId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCarriedFromProgressId() {
        return carriedFromProgressId;
    }
}
