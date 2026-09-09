package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
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

    @Column(name = "completion_percentage", nullable = false)
    private int completionPercentage;

    @Column(name = "last_progress_entry_id")
    private UUID lastProgressEntryId;

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

    public void markInProgress(UUID progressEntryId) {
        if (status != RoadmapItemProgressStatus.COMPLETED) {
            status = RoadmapItemProgressStatus.IN_PROGRESS;
            completedAt = null;
        }
        lastProgressEntryId = progressEntryId;
    }

    public void recordSkipped(UUID progressEntryId) {
        lastProgressEntryId = progressEntryId;
    }

    public void markCompleted(UUID progressEntryId, Instant completionTime) {
        status = RoadmapItemProgressStatus.COMPLETED;
        completionPercentage = 100;
        completedAt = completedAt == null ? completionTime : completedAt;
        lastProgressEntryId = progressEntryId;
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

    public int getCompletionPercentage() {
        return completionPercentage;
    }

    public UUID getLastProgressEntryId() {
        return lastProgressEntryId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
