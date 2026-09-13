package com.codegym.aiplanning.service.roadmap.progress;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LearningUnitProgressProjectorTest {

    private final LearningUnitProgressProjector projector =
            new LearningUnitProgressProjector();

    @Test
    void laterSkipDoesNotUndoACompletedLearningUnit() {
        ProgressEntry completed = entry(
                ProgressEntryStatus.COMPLETED,
                100,
                Instant.parse("2026-09-01T08:00:00Z"),
                null);
        ProgressEntry skippedReview = entry(
                ProgressEntryStatus.SKIPPED,
                0,
                Instant.parse("2026-09-02T08:00:00Z"),
                null);

        LearningUnitProgressProjection projection = projector.project(
                List.of(completed, skippedReview), null);

        assertThat(projection.status())
                .isEqualTo(RoadmapItemProgressStatus.COMPLETED);
        assertThat(projection.latestOutcome())
                .isEqualTo(ProgressEntryStatus.SKIPPED);
        assertThat(projection.completionPercentage()).isEqualTo(100);
        assertThat(projection.completedAt()).isEqualTo(completed.getRecordedAt());
    }

    @Test
    void correctionReplacesTheOriginalOutcomeAtItsLogicalPosition() {
        ProgressEntry completed = entry(
                ProgressEntryStatus.COMPLETED,
                100,
                Instant.parse("2026-09-01T08:00:00Z"),
                null);
        ProgressEntry laterPartial = entry(
                ProgressEntryStatus.PARTIALLY_COMPLETED,
                60,
                Instant.parse("2026-09-02T08:00:00Z"),
                null);
        ProgressEntry correction = entry(
                ProgressEntryStatus.SKIPPED,
                0,
                Instant.parse("2026-09-03T08:00:00Z"),
                completed.getId());

        LearningUnitProgressProjection projection = projector.project(
                List.of(completed, laterPartial, correction), null);

        assertThat(projection.status())
                .isEqualTo(RoadmapItemProgressStatus.IN_PROGRESS);
        assertThat(projection.latestOutcome())
                .isEqualTo(ProgressEntryStatus.PARTIALLY_COMPLETED);
        assertThat(projection.completionPercentage()).isEqualTo(60);
        assertThat(projection.lastProgressEntryId()).isEqualTo(laterPartial.getId());
    }

    @Test
    void correctionCanExplicitlyRemoveTheOnlyCompletion() {
        ProgressEntry completed = entry(
                ProgressEntryStatus.COMPLETED,
                100,
                Instant.parse("2026-09-01T08:00:00Z"),
                null);
        ProgressEntry correction = entry(
                ProgressEntryStatus.SKIPPED,
                0,
                Instant.parse("2026-09-02T08:00:00Z"),
                completed.getId());

        LearningUnitProgressProjection projection = projector.project(
                List.of(completed, correction), null);

        assertThat(projection.status())
                .isEqualTo(RoadmapItemProgressStatus.NOT_STARTED);
        assertThat(projection.latestOutcome())
                .isEqualTo(ProgressEntryStatus.SKIPPED);
        assertThat(projection.completionPercentage()).isZero();
        assertThat(projection.completedAt()).isNull();
    }

    private ProgressEntry entry(
            ProgressEntryStatus status,
            int percentage,
            Instant recordedAt,
            UUID supersedesEntryId) {
        ProgressEntry entry = ProgressEntry.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                30,
                percentage,
                null,
                null,
                null,
                null,
                supersedesEntryId);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entry, "recordedAt", recordedAt);
        return entry;
    }
}
