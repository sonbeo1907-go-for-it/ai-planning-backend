package com.codegym.aiplanning.controller.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeTopicDto(
        UUID id,
        String title,
        String description,
        int orderIndex,
        int estimatedMinutes,
        boolean isMastered,
        String status,
        int completionPercentage,
        Instant masteredAt,
        List<KnowledgeLearningUnitDto> learningUnits) {}
