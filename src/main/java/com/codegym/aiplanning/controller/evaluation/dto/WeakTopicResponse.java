package com.codegym.aiplanning.controller.evaluation.dto;

import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeakTopicResponse(
        UUID id,
        UUID roadmapId,
        UUID roadmapItemId,
        String topicTitle,
        String milestoneTitle,
        WeakTopicStatus status,
        WeakTopicTrigger triggerSource,
        BigDecimal lastQuizScore,
        Integer lastUnderstandingRating,
        Instant unresolvedAt,
        Instant masteredAt
) {}
