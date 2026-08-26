package com.codegym.aiplanning.controller.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizQuestionResponse(
        UUID id,
        UUID roadmapItemId,
        String questionText,
        List<QuizOptionDto> options,
        String userAnswer,
        Boolean isCorrect,
        String explanation
) {}
