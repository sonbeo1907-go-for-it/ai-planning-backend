package com.codegym.aiplanning.controller.report.dto;

import java.util.UUID;

public record MasterPlanProgressDto(
        UUID roadmapId,
        String title,
        double completionPercentage,
        int completedTopics,
        int totalTopics,
        int completedLearningUnits,
        int totalLearningUnits) {}
