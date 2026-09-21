package com.codegym.aiplanning.controller.evaluation.dto;

import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record MasteryCheckResultResponse(
        UUID weakTopicId,
        UUID quizId,
        UUID attemptId,
        BigDecimal quizScore,
        int correctCount,
        int totalCount,
        boolean isMastered,
        WeakTopicStatus weakTopicStatus,
        boolean canRetry,
        String message
) {}
