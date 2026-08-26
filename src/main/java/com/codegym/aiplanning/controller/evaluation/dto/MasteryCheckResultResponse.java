package com.codegym.aiplanning.controller.evaluation.dto;

import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record MasteryCheckResultResponse(
        UUID weakTopicId,
        BigDecimal quizScore,
        boolean isMastered,
        WeakTopicStatus weakTopicStatus,
        String message
) {}
