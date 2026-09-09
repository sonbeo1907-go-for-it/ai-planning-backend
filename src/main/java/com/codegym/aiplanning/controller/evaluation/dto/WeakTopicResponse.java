package com.codegym.aiplanning.controller.evaluation.dto;

import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeakTopicResponse(
        UUID id,
        UUID roadmapId,
        UUID roadmapVersionId,
        UUID roadmapItemId,
        RoadmapItemType targetItemType,
        UUID learningUnitId,
        String learningUnitTitle,
        UUID topicId,
        String topicTitle,
        UUID milestoneId,
        String milestoneTitle,
        WeakTopicStatus status,
        WeakTopicTrigger triggerSource,
        BigDecimal lastQuizScore,
        Integer lastUnderstandingRating,
        Instant unresolvedAt,
        Instant masteredAt
) {}
