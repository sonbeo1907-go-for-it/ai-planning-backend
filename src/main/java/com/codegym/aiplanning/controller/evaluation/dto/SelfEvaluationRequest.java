package com.codegym.aiplanning.controller.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SelfEvaluationRequest(
        @NotNull(message = "Overall rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        @Schema(description = "Self-rating on understanding from 1 to 5 stars", example = "4")
        Integer overallRating,

        @Size(max = 2000, message = "Feedback note must not exceed 2000 characters")
        @Schema(description = "Optional personal notes or questions", example = "Phần Spring Security Filter Chain cần thực hành thêm")
        String feedbackNote
) {}
