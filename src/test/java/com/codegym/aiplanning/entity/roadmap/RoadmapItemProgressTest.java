package com.codegym.aiplanning.entity.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapItemProgressTest {

    @Test
    void partialOutcomeRetainsItsPercentageInCurrentSnapshot() {
        RoadmapItemProgress progress = newProgress();
        UUID progressEntryId = UUID.randomUUID();

        progress.markInProgress(progressEntryId, 50);

        assertThat(progress.getStatus())
                .isEqualTo(RoadmapItemProgressStatus.IN_PROGRESS);
        assertThat(progress.getLatestOutcome())
                .isEqualTo(ProgressEntryStatus.PARTIALLY_COMPLETED);
        assertThat(progress.getCompletionPercentage()).isEqualTo(50);
        assertThat(progress.getLastProgressEntryId()).isEqualTo(progressEntryId);
    }

    @Test
    void skippedOutcomeDoesNotErasePreviouslyRecordedPartialPercentage() {
        RoadmapItemProgress progress = newProgress();
        progress.markInProgress(UUID.randomUUID(), 50);
        UUID skippedEntryId = UUID.randomUUID();

        progress.recordSkipped(skippedEntryId);

        assertThat(progress.getStatus())
                .isEqualTo(RoadmapItemProgressStatus.IN_PROGRESS);
        assertThat(progress.getLatestOutcome())
                .isEqualTo(ProgressEntryStatus.SKIPPED);
        assertThat(progress.getCompletionPercentage()).isEqualTo(50);
        assertThat(progress.getLastProgressEntryId()).isEqualTo(skippedEntryId);
    }

    @Test
    void laterPartialReviewDoesNotSilentlyUndoCompletedUnit() {
        RoadmapItemProgress progress = newProgress();
        progress.markCompleted(UUID.randomUUID(), Instant.now());

        progress.markInProgress(UUID.randomUUID(), 50);

        assertThat(progress.getStatus())
                .isEqualTo(RoadmapItemProgressStatus.COMPLETED);
        assertThat(progress.getLatestOutcome())
                .isEqualTo(ProgressEntryStatus.PARTIALLY_COMPLETED);
        assertThat(progress.getCompletionPercentage()).isEqualTo(100);
        assertThat(progress.getCompletedAt()).isNotNull();
    }

    private RoadmapItemProgress newProgress() {
        return RoadmapItemProgress.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}
