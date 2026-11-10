package com.codegym.aiplanning.controller.guidance.dto;

import com.codegym.aiplanning.common.api.PageResponse;
import java.util.UUID;

public record TaskGuidanceOverviewResponse(
        UUID guidanceId,
        UUID dailyPlanVersionId,
        UUID dailyPlanItemId,
        TaskGuidanceRevisionResponse latestRevision,
        PageResponse<TaskGuidanceRevisionSummaryResponse> revisions) {}
