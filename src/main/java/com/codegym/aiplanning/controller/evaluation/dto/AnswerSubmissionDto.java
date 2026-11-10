package com.codegym.aiplanning.controller.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AnswerSubmissionDto(
        @NotNull(message = "Question ID is required")
        UUID questionId,

        @NotBlank(message = "Selected option is required")
        String selectedOption
) {}
