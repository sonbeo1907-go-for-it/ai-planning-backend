package com.codegym.aiplanning.controller.report.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeLearningUnitDto(
        UUID id,
        String title,
        int orderIndex,
        int estimatedMinutes,
        boolean isMastered,
        String status,
        Instant masteredAt) {}
