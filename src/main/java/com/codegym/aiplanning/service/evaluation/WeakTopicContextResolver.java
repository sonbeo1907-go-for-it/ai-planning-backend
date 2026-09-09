package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import java.util.List;
import java.util.UUID;

public interface WeakTopicContextResolver {

    record WeakTopicPromptContext(
            UUID weakTopicId,
            UUID targetItemId,
            RoadmapItemType targetItemType,
            UUID learningUnitId,
            String learningUnitTitle,
            UUID topicId,
            String topicTitle,
            UUID milestoneId,
            String milestoneTitle,
            Integer lastRating,
            Double lastScore
    ) {}

    List<WeakTopicPromptContext> resolveUnresolvedWeakTopics(
            UUID userId,
            UUID roadmapVersionId);
}
