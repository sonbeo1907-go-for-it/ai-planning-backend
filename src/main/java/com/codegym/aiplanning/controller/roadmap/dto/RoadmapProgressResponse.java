package com.codegym.aiplanning.controller.roadmap.dto;

import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import java.util.List;
import java.util.UUID;

public record RoadmapProgressResponse(
        UUID roadmapId,
        UUID roadmapVersionId,
        int completedTopics,
        int totalTopics,
        double completionPercentage,
        List<TopicProgress> topics) {

    public record TopicProgress(
            UUID roadmapItemId,
            String title,
            RoadmapItemProgressStatus status,
            int completionPercentage,
            int completedLearningUnits,
            int totalLearningUnits,
            List<LearningUnitProgress> learningUnits) {}

    public record LearningUnitProgress(
            UUID roadmapItemId,
            String title,
            RoadmapItemProgressStatus status,
            int completionPercentage) {}
}
