package com.codegym.aiplanning.service.roadmap.progress;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builds the current Learning Unit snapshot from immutable progress history. */
public class LearningUnitProgressProjector {

    public LearningUnitProgressProjection project(
            List<ProgressEntry> history, RoadmapItemProgress existingSnapshot) {
        boolean carriedSnapshot = existingSnapshot != null
                && existingSnapshot.getCarriedFromProgressId() != null;
        RoadmapItemProgressStatus status = carriedSnapshot
                ? existingSnapshot.getStatus()
                : RoadmapItemProgressStatus.NOT_STARTED;
        ProgressEntryStatus latestOutcome = carriedSnapshot
                ? existingSnapshot.getLatestOutcome()
                : null;
        int percentage = carriedSnapshot
                ? existingSnapshot.getCompletionPercentage()
                : 0;
        Instant completedAt = carriedSnapshot
                ? existingSnapshot.getCompletedAt()
                : null;
        UUID lastEntryId = null;

        for (ProgressEntry entry : ProgressHistoryResolver.effectiveEntries(history)) {
            latestOutcome = entry.getStatus();
            lastEntryId = entry.getId();
            if (entry.getStatus() == ProgressEntryStatus.COMPLETED) {
                status = RoadmapItemProgressStatus.COMPLETED;
                percentage = 100;
                if (completedAt == null) {
                    completedAt = entry.getRecordedAt();
                }
            } else if (entry.getStatus() == ProgressEntryStatus.PARTIALLY_COMPLETED
                    && status != RoadmapItemProgressStatus.COMPLETED) {
                status = RoadmapItemProgressStatus.IN_PROGRESS;
                percentage = normalizedPartialPercentage(entry.getCompletionPercentage());
                completedAt = null;
            }
        }

        return new LearningUnitProgressProjection(
                status,
                latestOutcome,
                percentage,
                lastEntryId,
                completedAt);
    }

    private int normalizedPartialPercentage(Integer percentage) {
        int value = percentage == null ? 1 : percentage;
        return Math.max(1, Math.min(99, value));
    }
}
