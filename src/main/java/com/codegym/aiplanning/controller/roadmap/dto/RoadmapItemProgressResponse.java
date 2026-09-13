package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;

public record RoadmapItemProgressResponse(
        RoadmapItemProgressStatus completionState,
        ProgressEntryStatus latestOutcome,
        int completionPercentage,
        Integer completedLearningUnits,
        Integer totalLearningUnits) {

    public static RoadmapItemProgressResponse from(
            RoadmapProgressResponse.TopicProgress progress) {
        if (progress == null) {
            return null;
        }
        return new RoadmapItemProgressResponse(
                progress.status(),
                null,
                progress.completionPercentage(),
                progress.completedLearningUnits(),
                progress.totalLearningUnits());
    }

    public static RoadmapItemProgressResponse from(
            RoadmapProgressResponse.LearningUnitProgress progress) {
        if (progress == null) {
            return null;
        }
        return new RoadmapItemProgressResponse(
                progress.status(),
                progress.latestOutcome(),
                progress.completionPercentage(),
                null,
                null);
    }
}
