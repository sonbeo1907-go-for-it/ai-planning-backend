package com.codegym.aiplanning.controller.guidance.dto;

import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevisionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskGuidanceRevisionResponse(
        UUID guidanceId,
        UUID revisionId,
        UUID dailyPlanVersionId,
        UUID dailyPlanItemId,
        int revisionNumber,
        TaskGuidanceRevisionStatus status,
        boolean latest,
        boolean stale,
        String objective,
        String taskSummary,
        List<TaskStepGuidanceResponse> stepGuidances,
        List<TaskGuidanceReferenceResponse> references,
        Instant generatedAt) {}
