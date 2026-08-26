package com.codegym.aiplanning.controller.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyEvaluationResponse(
        UUID id,
        UUID dailyPlanId,
        LocalDate evaluationDate,
        BigDecimal quizScore,
        Boolean quizPassed,
        Integer overallRating,
        String feedbackNote,
        Instant createdAt
) {}
