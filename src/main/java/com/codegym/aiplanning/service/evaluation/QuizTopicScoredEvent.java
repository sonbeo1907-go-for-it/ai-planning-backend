package com.codegym.aiplanning.service.evaluation;

import java.math.BigDecimal;
import java.util.UUID;

public record QuizTopicScoredEvent(
        UUID userId,
        UUID roadmapId,
        UUID roadmapItemId,
        BigDecimal score) {}
