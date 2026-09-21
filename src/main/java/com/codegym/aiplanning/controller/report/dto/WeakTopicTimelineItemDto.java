package com.codegym.aiplanning.controller.report.dto;

import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WeakTopicTimelineItemDto(
        UUID weakTopicId,
        UUID roadmapId,
        String roadmapTitle,
        UUID learningUnitId,
        String learningUnitTitle,
        UUID topicId,
        String topicTitle,
        UUID milestoneId,
        String milestoneTitle,
        WeakTopicStatus status,
        WeakTopicTrigger triggerSource,
        BigDecimal initialQuizScore,
        Integer initialRating,
        Instant detectedAt,
        Instant masteredAt,
        Long daysToMaster) {}
