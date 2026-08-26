package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import java.util.List;
import java.util.UUID;

/**
 * Untrusted context prepared for task-suggestion AI generation (US-TSK-AI).
 */
public record TaskSuggestionContext(
        UUID dailyPlanItemId,
        String title,
        String description,
        DailyTaskCategory category,
        Integer plannedMinutes,
        RoadmapTopicContext roadmapTopic,
        String goal,
        List<SourceDocument> sources) {

    public record RoadmapTopicContext(
            UUID roadmapItemId,
            String milestoneTitle,
            String title,
            String description) {}

    public record SourceDocument(
            UUID documentId,
            String sourceType,
            String title,
            String content) {}
}
