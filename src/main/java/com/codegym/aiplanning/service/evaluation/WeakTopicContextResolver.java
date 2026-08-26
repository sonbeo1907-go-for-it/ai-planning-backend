package com.codegym.aiplanning.service.evaluation;

import java.util.List;
import java.util.UUID;

public interface WeakTopicContextResolver {

    record WeakTopicPromptContext(
            UUID weakTopicId,
            UUID roadmapItemId,
            String topicTitle,
            String milestoneTitle,
            Integer lastRating,
            Double lastScore
    ) {}

    List<WeakTopicPromptContext> resolveUnresolvedWeakTopics(UUID userId, UUID roadmapId);
}
