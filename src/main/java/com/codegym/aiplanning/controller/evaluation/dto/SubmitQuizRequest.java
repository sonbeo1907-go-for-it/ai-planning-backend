package com.codegym.aiplanning.controller.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubmitQuizRequest(
        @NotEmpty(message = "Answers list must not be empty")
        List<@Valid AnswerSubmissionDto> answers
) {}
