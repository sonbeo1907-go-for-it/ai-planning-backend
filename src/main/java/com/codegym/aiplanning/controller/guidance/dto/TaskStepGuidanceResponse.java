package com.codegym.aiplanning.controller.guidance.dto;

import java.util.List;
import java.util.UUID;

public record TaskStepGuidanceResponse(
        UUID id,
        UUID taskStepId,
        long taskStepEntityVersion,
        int orderIndex,
        String instructions,
        String expectedResult,
        String tips,
        String cautions,
        String prerequisites,
        List<TaskGuidanceReferenceResponse> references) {}
