package com.codegym.aiplanning.controller.roadmap.dto;

import java.util.UUID;

public record RoadmapProgressSummaryResponse(
        UUID roadmapVersionId,
        int completedTopics,
        int totalTopics,
        double completionPercentage) {

    public static RoadmapProgressSummaryResponse from(RoadmapProgressResponse progress) {
        if (progress == null || progress.roadmapVersionId() == null) {
            return null;
        }
        return new RoadmapProgressSummaryResponse(
                progress.roadmapVersionId(),
                progress.completedTopics(),
                progress.totalTopics(),
                progress.completionPercentage());
    }
}
