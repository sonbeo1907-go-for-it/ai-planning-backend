package com.codegym.aiplanning.controller.guidance.dto;

import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevisionStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskGuidanceRevisionSummaryResponse(
        UUID revisionId,
        int revisionNumber,
        TaskGuidanceRevisionStatus status,
        boolean latest,
        boolean stale,
        String objective,
        Instant generatedAt) {}
