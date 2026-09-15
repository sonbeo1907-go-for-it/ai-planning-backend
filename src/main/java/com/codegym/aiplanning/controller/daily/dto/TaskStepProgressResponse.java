package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Runtime completion summary for required Task Steps")
public record TaskStepProgressResponse(
        int requiredCount,
        int completedRequiredCount,
        double completionPercentage,
        boolean allRequiredStepsCompleted) {}

