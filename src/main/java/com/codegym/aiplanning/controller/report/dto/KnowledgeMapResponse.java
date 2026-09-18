package com.codegym.aiplanning.controller.report.dto;

import java.util.List;
import java.util.UUID;

public record KnowledgeMapResponse(
        UUID roadmapId,
        String roadmapTitle,
        int totalMilestones,
        int totalTopics,
        int masteredTopics,
        int totalLearningUnits,
        int masteredLearningUnits,
        double masteryPercentage,
        List<KnowledgeMilestoneDto> milestones) {}
