package com.codegym.aiplanning.controller.evaluation.dto;

import com.codegym.aiplanning.entity.evaluation.QuizStatus;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizDetailResponse(
        UUID id,
        UUID dailyPlanId,
        UUID roadmapId,
        QuizType quizType,
        QuizStatus status,
        BigDecimal score,
        Boolean passed,
        Instant submittedAt,
        List<QuizQuestionResponse> questions
) {}
