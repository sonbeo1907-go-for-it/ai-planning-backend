package com.codegym.aiplanning.controller.daily.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Task Step checklist and parent task after one atomic completion operation")
public record CompleteTaskStepAndRecordProgressResponse(
        DailyPlanTaskStepsResponse taskSteps,
        DailyPlanItemResponse task) {}
