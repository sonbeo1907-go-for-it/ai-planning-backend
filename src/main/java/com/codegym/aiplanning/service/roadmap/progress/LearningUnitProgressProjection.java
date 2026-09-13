package com.codegym.aiplanning.service.roadmap.progress;

import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import java.time.Instant;
import java.util.UUID;

public record LearningUnitProgressProjection(
        RoadmapItemProgressStatus status,
        ProgressEntryStatus latestOutcome,
        int completionPercentage,
        UUID lastProgressEntryId,
        Instant completedAt) {}
